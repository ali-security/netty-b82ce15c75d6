/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util.internal;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

/**
 * Unit test for {@link PlatformDependent}.
 */
public class PlatformDependentTest {

    @Test
    public void testCreateTempFileWithNullDirectory() throws IOException {
        File file = PlatformDependent.createTempFile("netty-test-", ".tmp", null);
        try {
            assertNotNull(file);
            assertTrue(file.exists());
            assertTrue(file.getName().startsWith("netty-test-"));
            assertTrue(file.getName().endsWith(".tmp"));
            assertEquals(0, file.length());
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileWithDirectory() throws IOException {
        File dir = createTempDirectory();
        try {
            File file = PlatformDependent.createTempFile("netty-test-", ".tmp", dir);
            try {
                assertNotNull(file);
                assertTrue(file.exists());
                assertTrue(file.getName().startsWith("netty-test-"));
                assertTrue(file.getName().endsWith(".tmp"));
                assertEquals(dir.getAbsolutePath(), file.getParentFile().getAbsolutePath());
            } finally {
                file.delete();
            }
        } finally {
            dir.delete();
        }
    }

    @Test(expected = IOException.class)
    public void testCreateTempFileWithMissingDirectory() throws IOException {
        File dir = createTempDirectory();
        assertTrue(dir.delete());
        PlatformDependent.createTempFile("netty-test-", ".tmp", dir);
    }

    @Test
    public void testCreateTempFilePermissions() throws Exception {
        File file = PlatformDependent.createTempFile("netty-perm-", ".tmp", null);
        try {
            assertTrue(file.canRead());
            assertTrue(file.canWrite());
            assertNotReadableByOthers(file);
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateTempFileWithDirectoryPermissions() throws Exception {
        File dir = createTempDirectory();
        try {
            File file = PlatformDependent.createTempFile("netty-perm-", ".tmp", dir);
            try {
                assertTrue(file.canRead());
                assertTrue(file.canWrite());
                assertNotReadableByOthers(file);
            } finally {
                file.delete();
            }
        } finally {
            dir.delete();
        }
    }

    @Test
    public void testCreateTempFileLegacyPermissions() throws Exception {
        // The java.io.File based fallback used before Java 7 must restrict the permissions as well.
        File file = PlatformDependent.createTempFileLegacy("netty-perm-", ".tmp", null);
        try {
            assertTrue(file.canRead());
            assertNotReadableByOthers(file);
        } finally {
            file.delete();
        }

        File dir = createTempDirectory();
        try {
            file = PlatformDependent.createTempFileLegacy("netty-perm-", ".tmp", dir);
            try {
                assertEquals(dir.getAbsolutePath(), file.getParentFile().getAbsolutePath());
                assertTrue(file.canRead());
                assertNotReadableByOthers(file);
            } finally {
                file.delete();
            }
        } finally {
            dir.delete();
        }
    }

    @Test
    public void testRestrictPermissionsFailure() throws Exception {
        // java.io.File.setReadable(boolean, boolean) is only available since Java 6.
        assumeTrue(DetectionUtil.javaVersion() >= 6);

        File file = PlatformDependent.createTempFileLegacy("netty-perm-", ".tmp", null);
        assertTrue(file.delete());
        try {
            // Changing the permissions of a non-existing file fails, which must not be ignored.
            PlatformDependent.restrictPermissions(file);
            fail();
        } catch (IOException e) {
            assertEquals("Failed to set permissions on temporary file " + file, e.getMessage());
        }
    }

    private static File createTempDirectory() throws IOException {
        File dir = File.createTempFile("netty-dir-", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());
        return dir;
    }

    /**
     * Asserts that the given file can not be read by any user other than its owner.
     * Only performed on Java 7+ and non-Windows platforms, where the POSIX
     * file permissions can be inspected.
     */
    public static void assertNotReadableByOthers(File file) throws Exception {
        assumeTrue(DetectionUtil.javaVersion() >= 7 && !DetectionUtil.isWindows());

        Set<String> permissions = posixFilePermissions(file);
        assertTrue(permissions.toString(), permissions.contains("OWNER_READ"));
        assertFalse(permissions.toString(), permissions.contains("GROUP_READ"));
        assertFalse(permissions.toString(), permissions.contains("OTHERS_READ"));
    }

    /**
     * Returns the names of the {@code PosixFilePermission}s of the given file
     * (Java 7+ only, accessed via reflection as the project targets Java 5).
     */
    private static Set<String> posixFilePermissions(File file) throws Exception {
        Class<?> filesClass = Class.forName("java.nio.file.Files");
        Class<?> pathClass = Class.forName("java.nio.file.Path");
        Object noLinkOptions = Array.newInstance(Class.forName("java.nio.file.LinkOption"), 0);
        Object path = File.class.getMethod("toPath").invoke(file);
        Set<?> permissions = (Set<?>) filesClass.getMethod(
                "getPosixFilePermissions", pathClass, noLinkOptions.getClass()).invoke(null, path, noLinkOptions);

        Set<String> names = new HashSet<String>();
        for (Object permission: permissions) {
            names.add(permission.toString());
        }
        return names;
    }
}
