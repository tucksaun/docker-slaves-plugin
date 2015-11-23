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
import hudson.matrix.MatrixProject;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Queue;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Definition for a set of containers to host the build.
 * @author <a href="mailto:tugdual.saunier@blackfire.io">Tugdual Saunier</a>
 */
public class MatrixProjectContainersDefinition extends JobProperty {

    private final boolean forcePull;

    private static final String ImagePrefix = "docker:";
    private static final String ConstraintPrefix = "constraint:";

    @DataBoundConstructor
    public MatrixProjectContainersDefinition(boolean forcePull) {
        this.forcePull = forcePull;
    }

    public ImageIdContainerDefinition getBuildHostImage(Queue.Item bi) {
        String label = bi.getAssignedLabel().toString();

        for(String subLabel: StringUtils.split(label, ' ')) {
            if (subLabel.startsWith(ImagePrefix)) {
                return new ImageIdContainerDefinition(subLabel.substring(ImagePrefix.length()), forcePull);
            }
        }

        return null;
    }

    public String getConstraint(Queue.Item bi) {
        String label = bi.getAssignedLabel().toString();

        for(String subLabel: StringUtils.split(label, ' ')) {
            if (subLabel.startsWith(ConstraintPrefix)) {
                return subLabel.substring(ConstraintPrefix.length());
            }
        }

        return null;
    }

    @Extension(ordinal = 98, optional = true)
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            try {
                return MatrixProject.class.isAssignableFrom(jobType);
            } catch (NoClassDefFoundError e) {
                return false;
            }
        }

        @Override
        public String getDisplayName() {
            return "Labels as container names to host the build";
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData.isNullObject())  return null;
            JSONObject containersDefinition = formData.getJSONObject("matrixContainersDefinition");
            if (containersDefinition.isNullObject()) return null;
            return req.bindJSON(MatrixProjectContainersDefinition.class, containersDefinition);
        }
    }

}
