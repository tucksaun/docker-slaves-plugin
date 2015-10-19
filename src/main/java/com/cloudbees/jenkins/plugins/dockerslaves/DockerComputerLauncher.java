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

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Launchs initials containers
 */
public class DockerComputerLauncher extends ComputerLauncher {

    private static final int BASE_RETRY_DELAY = 2000;
    private static final int MAX_RETRY_DELAY = 60000;

    private static final Logger LOGGER = Logger.getLogger(DockerComputerLauncher.class.getName());

    public DockerComputerLauncher() {
    }

    @Override
    public void launch(final SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        if (computer instanceof DockerComputer) {
            launch((DockerComputer) computer, listener);
        } else {
            throw new IllegalArgumentException("This launcher only can handle DockerComputer");
        }
    }

    public void launch(final DockerComputer computer, TaskListener listener) throws IOException, InterruptedException {
        // we need to capture taskListener here, as it's a private field of Computer
        DockerSlaves plugin = DockerSlaves.get();
        DockerJobContainersProvisioner provisioner = computer.createProvisioner(listener);
        int retryDelay = BASE_RETRY_DELAY;

        while (!plugin.incrementContainerCount()) {
            LOGGER.log(
                    Level.INFO,
                    "Docker capping limit reached with {0}/{1} container(s) for {2}: postponing slave launch by {3} ms.",
                    new Object[] { plugin.getContainerCount().get(), plugin.getContainerCap(), computer, retryDelay }
            );
            Thread.sleep(retryDelay);
            retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY);
        }

        provisioner.prepareRemotingContainer();
        provisioner.launchRemotingContainer(computer, listener);

        // TODO catch launch failure and mark the build as Result.NOT_BUILT
    }
}
