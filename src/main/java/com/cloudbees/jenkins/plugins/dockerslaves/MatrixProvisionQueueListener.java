/*
 * The MIT License
 *
 *  Copyright (c) 2015, Blackfire.io
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
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.model.labels.LabelAssignmentAction;
import hudson.model.queue.QueueListener;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
* @author <a href="mailto:tugdual.saunier@blackfire.io">Tugdual Saunier</a>
*/
@Extension(optional = true, ordinal = 100)
public class MatrixProvisionQueueListener extends QueueListener {

    @Override
    public void onEnterBuildable(final Queue.BuildableItem bi) {
        if (bi.task instanceof MatrixConfiguration) {
            MatrixConfiguration job = (MatrixConfiguration) bi.task;

            if (job.getParent() instanceof MatrixProject) {
                MatrixProjectContainersDefinition def = job.getParent().getProperty(MatrixProjectContainersDefinition.class);

                if (def == null) {
                    LOGGER.info("No parent definition available for " + job.toString() + "#" + job.getNextBuildNumber());
                    return;
                }

                ImageIdContainerDefinition imageId = def.getBuildHostImage(bi);
                if (imageId == null) {
                    LOGGER.info("No imageId available to create definition for " + job.toString() + "#" + job.getNextBuildNumber());
                    return;
                }

                LOGGER.info("Creating new definition for " + job.toString() + "#" + job.getNextBuildNumber() + " with " + imageId.getImage());

                try {
                    JobProperty p;
                    do {
                        p = job.removeProperty(JobBuildsContainersDefinition.class);
                    } while (p != null);

                    job.addProperty(new JobBuildsContainersDefinition(
                            imageId,
                            null
                    ));
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                List<LabelAssignmentAction> assignmentActions = bi.getActions(LabelAssignmentAction.class);
                List<Action> current = bi.getActions();
                current.removeAll(assignmentActions);
            }
        }

    }

    private static final Logger LOGGER = Logger.getLogger(MatrixProvisionQueueListener.class.getName());
}
