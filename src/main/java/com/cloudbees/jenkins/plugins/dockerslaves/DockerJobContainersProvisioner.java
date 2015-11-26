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

import hudson.Launcher;
import hudson.Proc;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.CommandLauncher;
import hudson.slaves.SlaveComputer;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provision {@link ContainerInstance}s based on ${@link JobBuildsContainersDefinition} to provide a queued task
 * an executor.
 */
public class DockerJobContainersProvisioner {

    private static final int BASE_RETRY_DELAY = 2000, MAX_RETRY_DELAY = BASE_RETRY_DELAY * 30;

    private final JobBuildsContainersContext context;

    private final TaskListener slaveListener;

    private final DockerDriver driver;

    private final Launcher localLauncher;

    private final JobBuildsContainersDefinition spec;

    private final String remotingImage;
    private final String scmImage;
    private String buildImage;

    private static final Logger LOGGER = Logger.getLogger(DockerJobContainersProvisioner.class.getName());

    public DockerJobContainersProvisioner(Job job, DockerServerEndpoint dockerHost, TaskListener slaveListener, String remotingImage, String scmImage) throws IOException, InterruptedException {
        this.slaveListener = slaveListener;
        this.driver = new DockerDriver(dockerHost, job);
        localLauncher = new Launcher.LocalLauncher(slaveListener);
        spec = (JobBuildsContainersDefinition) job.getProperty(JobBuildsContainersDefinition.class);

        this.remotingImage = remotingImage;
        this.scmImage = scmImage;
        context = new JobBuildsContainersContext();

        // TODO define a configurable volume strategy to retrieve a (maybe persistent) workspace
        // could rely on docker volume driver
        // in the meantime, we just rely on previous build's remoting container as a data volume container

        // reuse previous remoting container to retrieve workspace
        Run lastBuild = job.getBuilds().getLastBuild();
        if (lastBuild != null) {
            JobBuildsContainersContext previousContext = (JobBuildsContainersContext) lastBuild.getAction(JobBuildsContainersContext.class);
            if (previousContext != null && previousContext.getRemotingContainer() != null) {
                context.setRemotingContainer(previousContext.getRemotingContainer());
            }
        }
    }

    public JobBuildsContainersContext getContext() {
        return context;
    }

    public void prepareRemotingContainer() throws IOException, InterruptedException {
        // if remoting container already exists, we reuse it
        if (context.getRemotingContainer() != null) {
            if (driver.hasContainer(localLauncher, context.getRemotingContainer().getId())) {
                return;
            }
        }
        final ContainerInstance remotingContainer = driver.createRemotingContainer(localLauncher, remotingImage);
        context.setRemotingContainer(remotingContainer);
    }

    public void launchRemotingContainer(final SlaveComputer computer, TaskListener listener) {
        int retryDelay = BASE_RETRY_DELAY;

        while (true) {
            try {
                synchronized (driver.containerCountLock) {
                    int containerCap = DockerSlaves.get().getContainerCap();

                    if (driver.containerCountLock.containerCount < containerCap) {
                        driver.containerCountLock.containerCount++;
                        LOGGER.log(
                                Level.WARNING,
                                "Docker capping limit NOT reached with {0}/{1} container(s) for {2}: launching.",
                                new Object[]{driver.containerCountLock.containerCount, containerCap, context.getRemotingContainer().getImageName(), retryDelay}
                        );
                        break;
                    }

                    LOGGER.log(
                            Level.WARNING,
                            "Docker capping limit reached with {0}/{1} container(s) for {2}: postponing slave launch by {3} ms.",
                            new Object[] { driver.containerCountLock.containerCount, containerCap, context.getRemotingContainer().getImageName(), retryDelay }
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY);
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        ArgumentListBuilder args = new ArgumentListBuilder()
                .add("start")
                .add("-ia", context.getRemotingContainer().getId());
        driver.prependArgs(args);
        CommandLauncher launcher = new CommandLauncher(args.toString(), driver.dockerEnv.env());
        launcher.launch(computer, listener);
    }

    public BuildContainer newBuildContainer(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException {
        if (!context.isPreScm() && spec.getSideContainers().size() > 0 && context.getSideContainers().size() == 0) {
            // In a ideal world we would run side containers when DockerSlave.DockerSlaveSCMListener detect scm checkout completed
            // but then we don't have a ProcStarter reference. So do it first time a command is ran during the build
            // after scm checkout completed. We detect this is the first time as spec > context
            createSideContainers(starter, listener);
        }

        if (context.isPreScm()) {
            return newBuildContainer(starter, scmImage);
        } else {
            if (buildImage == null) buildImage = spec.getBuildHostImage().getImage(driver, starter, listener);
            return newBuildContainer(starter, buildImage);
        }
    }

    private void createSideContainers(Launcher.ProcStarter starter, TaskListener listener) throws IOException, InterruptedException {
        for (SideContainerDefinition definition : spec.getSideContainers()) {
            final String name = definition.getName();
            final String image = definition.getSpec().getImage(driver, starter, listener);
            listener.getLogger().println("Starting " + name + " container");
            ContainerInstance container = new ContainerInstance(image);
            context.getSideContainers().put(name, container);
            driver.launchSideContainer(localLauncher, container, context.getRemotingContainer());
        }
    }

    private BuildContainer newBuildContainer(Launcher.ProcStarter procStarter, String buildImage) {
        final ContainerInstance c = new ContainerInstance(context.isPreScm() ? scmImage : buildImage);
        context.getBuildContainers().add(c);
        return new BuildContainer(c, procStarter);
    }

    public void createBuildContainer(BuildContainer buildContainer) throws IOException, InterruptedException {
        driver.createBuildContainer(localLauncher, buildContainer.instance, context.getRemotingContainer(), buildContainer.procStarter);
    }

    public Proc startBuildContainer(BuildContainer buildContainer) throws IOException, InterruptedException {
        return driver.startContainer(localLauncher, buildContainer.instance.getId(), buildContainer.procStarter.stdout());
    }

    public void clean() throws IOException, InterruptedException {
        for (ContainerInstance instance : context.getSideContainers().values()) {
            driver.removeContainer(localLauncher, instance);
        }

        for (ContainerInstance instance : context.getBuildContainers()) {
            driver.removeContainer(localLauncher, instance);
        }

        driver.close();

        synchronized (driver.containerCountLock) {
            driver.containerCountLock.containerCount = Math.max(driver.containerCountLock.containerCount - 1, 0);
        }
    }

    public class BuildContainer {
        final ContainerInstance instance;
        final Launcher.ProcStarter procStarter;

        protected BuildContainer(ContainerInstance instance, Launcher.ProcStarter procStarter) {
            this.instance = instance;
            this.procStarter = procStarter;
        }

        public String getId() {
            return instance.getId();
        }

        public String getImageName() {
            return instance.getImageName();
        }
    }
}