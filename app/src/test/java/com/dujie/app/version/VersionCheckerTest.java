package com.dujie.app.version;

import org.junit.Assert;
import org.junit.Test;

public class VersionCheckerTest {

    @Test
    public void testNewerVersions() {
        Assert.assertTrue(VersionChecker.isNewer("v3.0.1", "3.0.0"));
        Assert.assertTrue(VersionChecker.isNewer("3.1.0", "3.0.9"));
        Assert.assertTrue(VersionChecker.isNewer("4.0.0", "3.99.99"));
        Assert.assertTrue(VersionChecker.isNewer("3.0.0", "2.9.9"));
    }

    @Test
    public void testSameOrOlderVersions() {
        Assert.assertFalse(VersionChecker.isNewer("v3.0.0", "3.0.0"));
        Assert.assertFalse(VersionChecker.isNewer("3.0.0", "3.0.1"));
        Assert.assertFalse(VersionChecker.isNewer("", "3.0.0"));
    }

    @Test
    public void testNonNumericSegments() {
        Assert.assertTrue(VersionChecker.isNewer("3.0.1-rc1", "3.0.0"));
        Assert.assertFalse(VersionChecker.isNewer("3.0.0-rc1", "3.0.0"));
    }
}
