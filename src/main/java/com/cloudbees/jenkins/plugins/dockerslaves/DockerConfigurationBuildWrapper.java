/*
 * The MIT License
 *
 *  Copyright (c) 2016, Blackfire.io
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */
package com.cloudbees.jenkins.plugins.dockerslaves;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.docker.commons.credentials.*;
import org.kohsuke.stapler.DataBoundConstructor;

import jenkins.tasks.SimpleBuildWrapper;

import javax.annotation.Nonnull;

/**
 * Build wrapper that decorates the build to inject Docker configuration.
 *
 * @author Tugdual Saunier
 */
public final class DockerConfigurationBuildWrapper extends SimpleBuildWrapper {

    private static final Logger LOGGER = Logger.getLogger(DockerConfigurationBuildWrapper.class.getName());

    /**
     * Create a new {@link DockerConfigurationBuildWrapper}.
     */
    @DataBoundConstructor
    public DockerConfigurationBuildWrapper() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // the directory needs to be outside workspace to avoid prying eyes
        FilePath dotDocker = workspace.child(".docker");
        dotDocker.mkdirs();

        Job job = build.getParent();
        JobBuildsContainersDefinition spec = (JobBuildsContainersDefinition) job.getProperty(JobBuildsContainersDefinition.class);
        if (spec == null) {
            return;
        }

        KeyMaterial dockerEnv = newKeyMaterialFactory(job, dotDocker).materialize();
        context.getEnv().putAll(dockerEnv.env());

        String constraint = spec.getConstraint();
        if (StringUtils.isNotBlank(constraint)) {
            context.getEnv().put("DOCKER_CONSTRAINT", constraint);
        } else {
            constraint = DockerSlaves.get().getDefaultConstraint();
            if (StringUtils.isNotBlank(constraint)) {
                context.getEnv().put("DOCKER_CONSTRAINT", constraint);
            }
        }

        FilePath dockerBin = dotDocker.child("docker");
        dockerBin.copyFrom(new FileInputStream("/usr/local/bin/docker"));
        dockerBin.chmod(0755);

        LOGGER.log(Level.INFO, "Docker configuration injected");
        context.setDisposer(new DisposerImpl());
    }

    /**
     * Makes the key materials available locally and returns {@link KeyMaterialFactory} that gives you the parameters
     * needed to access it.
     */
    private KeyMaterialFactory newKeyMaterialFactory(@Nonnull Item context, @Nonnull FilePath target) throws IOException, InterruptedException {
        // as a build step, your access to credentials are constrained by what the build
        // can access, hence Jenkins.getAuthentication()
        DockerServerCredentials creds=null;
        DockerServerEndpoint dockerHost = DockerSlaves.get().getDockerHost();
        if (dockerHost.getCredentialsId() != null) {
            List<DomainRequirement> domainRequirements = URIRequirementBuilder.fromUri(dockerHost.getUri()).build();
            domainRequirements.add(new DockerServerDomainRequirement());
            creds = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            DockerServerCredentials.class, context, Jenkins.getAuthentication(),
                            domainRequirements),
                    CredentialsMatchers.withId(dockerHost.getCredentialsId())
            );
        }

        // ServerKeyMaterialFactory.materialize creates a random subdir if one is needed:
        return dockerHost.newKeyMaterialFactory(target, creds);
    }

    /**
     * An optional callback to run at the end of the wrapped block.
     * Must be safely serializable, so it receives runtime context comparable to that of the original setup.
     */
    private static final class DisposerImpl extends Disposer {
        private static final long serialVersionUID = 1;

        /**
         * Attempt to clean up anything that was done in the initial setup.
         * @param build a build being run
         * @param workspace a workspace of the build
         * @param launcher a way to start commands
         * @param listener a way to report progress
         * @throws IOException if something fails; {@link AbortException} for user errors
         * @throws InterruptedException if tear down is interrupted
         */
        @Override public void tearDown(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            LOGGER.log(Level.INFO, "Cleaning Docker configuration injected");
            FilePath dotDocker = workspace.child(".docker");
            dotDocker.deleteRecursive();
        }
    }

    /**
     * Registers {@link DockerConfigurationBuildWrapper} as a {@link BuildWrapper}.
     */
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Inject Docker configuration inside environment";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
    }
}
