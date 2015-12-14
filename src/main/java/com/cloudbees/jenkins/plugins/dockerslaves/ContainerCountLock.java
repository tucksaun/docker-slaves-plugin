package com.cloudbees.jenkins.plugins.dockerslaves;

import java.util.Hashtable;
import java.util.Map;

/**
 * @author <a href="mailto:tugdual.saunier@blackfire.io">Tugdual Saunier</a>
 */
public class ContainerCountLock {
    private Map<String, Integer> containerCounts;

    private int containerCap = 0;

    private String defaultConstraint;


    public ContainerCountLock(int containerCap, String defaultConstraint) {
        containerCounts = new Hashtable<String, Integer>(2);
        setContainerCap(containerCap);
        setDefaultConstraint(defaultConstraint);
    }

    public void setDefaultConstraint(String defaultConstraint) {
        this.defaultConstraint = defaultConstraint;
    }

    public void setContainerCap(int containerCap) {
        this.containerCap = containerCap;
    }

    public int getCount(JobBuildsContainersContext context) {
        return containerCounts.getOrDefault(context.getConstraint(), 0);
    }

    public int getLimit(JobBuildsContainersContext context) {
        if (context.getConstraint() == defaultConstraint) {
            return containerCap;
        }

        return 2;
    }

    public boolean isLimitReach(JobBuildsContainersContext context)  {
        return getCount(context) >= getLimit(context);
    }

    public void increaseCount(JobBuildsContainersContext context) {
        containerCounts.put(context.getConstraint(), getCount(context)+1);
    }

    public void decreaseCount(JobBuildsContainersContext context) {
        containerCounts.put(context.getConstraint(), Math.max(getCount(context) - 1, 0));
    }
}
