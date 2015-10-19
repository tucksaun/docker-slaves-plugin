package com.cloudbees.jenkins.plugins.dockerslaves;

/**
 * @author <a href="mailto:tugdual.saunier@blackfire.io">Tugdual Saunier</a>
 */
public class ContainerCount {
    private int count = 0;

    public int get()
    {
        return count;
    }

    public int increment()
    {
        return ++count;
    }

    public int decrement()
    {
        return --count;
    }
}
