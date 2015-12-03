/*
 * The MIT License
 *
 *  Copyright (c) 2015, CloudBees, Inc.
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

import hudson.Extension;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Job;

import java.util.Collections;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Definition for a set of containers to host the build.
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class JobBuildsContainersDefinition extends JobProperty {

    private final ContainerDefinition buildHostImage;

    private final List<SideContainerDefinition> sideContainers;

    private final String constraint;

    @DataBoundConstructor
    public JobBuildsContainersDefinition(ContainerDefinition buildHostImage, List<SideContainerDefinition> sideContainers, String constraint) {
        this.buildHostImage = buildHostImage;
        this.sideContainers = sideContainers == null ? Collections.<SideContainerDefinition>emptyList() : sideContainers;
        this.constraint = constraint;
    }

    /**
     * When deserializing the config.xml file, XStream will instantiate a JobBuildsContainersDefinition
     * without going through the constructor; this means that any checks or default values that might
     * have been written in said constructor will be bypassed.
     * <p>
     * Fortunately, XStream calls the <code>readResolve</code> before the deserialized object
     * is returned to its parent. We simply recreate a JobBuildsContainersDefinition using the
     * deserialized values to replace the original one.
     *
     * @return a replacement JobBuildsContainersDefinition that went through the constructor
     */
    private Object readResolve() {
        return new JobBuildsContainersDefinition(buildHostImage, sideContainers, constraint);
    }

    public ContainerDefinition getBuildHostImage() {
        return buildHostImage;
        // return StringUtils.isBlank(buildHostImage) ? DockerSlaves.get().getDefaultBuildContainerImageName() : buildHostImage;
    }

    public List<SideContainerDefinition> getSideContainers() {
        return sideContainers;
    }

    public String getConstraint() {
        return constraint;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            MatrixProjectContainersDefinition.DescriptorImpl des = new MatrixProjectContainersDefinition.DescriptorImpl();

            return !des.isApplicable(jobType);
        }

        @Override
        public String getDisplayName() {
            return "Containers to host the build";
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData.isNullObject()) return null;
            JSONObject containersDefinition = formData.getJSONObject("containersDefinition");
            if (containersDefinition.isNullObject()) return null;
            return req.bindJSON(JobBuildsContainersDefinition.class, containersDefinition);
        }
    }

}
