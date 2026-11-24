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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility that provides operations whose implementation depends on the
 * current runtime environment, such as the Java version.
 */
public final class PlatformDependent {

    /**
     * {@code java.nio.file.Files.createTempFile(String, String, FileAttribute...)} (Java 7+)
     */
    private static final Method FILES_CREATE_TEMP_FILE;

    /**
     * {@code java.nio.file.Files.createTempFile(Path, String, String, FileAttribute...)} (Java 7+)
     */
    private static final Method FILES_CREATE_TEMP_FILE_IN_DIRECTORY;

    /**
     * {@code java.io.File.toPath()} (Java 7+)
     */
    private static final Method FILE_TO_PATH;

    /**
     * {@code java.nio.file.Path.toFile()} (Java 7+)
     */
    private static final Method PATH_TO_FILE;

    /**
     * An empty {@code java.nio.file.attribute.FileAttribute[]} (Java 7+)
     */
    private static final Object NO_FILE_ATTRIBUTES;

    /**
     * {@code java.io.File.setReadable(boolean, boolean)} (Java 6+)
     */
    private static final Method FILE_SET_READABLE;

    static {
        Method createTempFile = null;
        Method createTempFileInDirectory = null;
        Method toPath = null;
        Method toFile = null;
        Object noFileAttributes = null;
        if (DetectionUtil.javaVersion() >= 7) {
            try {
                Class<?> filesClass = Class.forName("java.nio.file.Files");
                Class<?> pathClass = Class.forName("java.nio.file.Path");
                Class<?> fileAttributeClass = Class.forName("java.nio.file.attribute.FileAttribute");
                Object attrs = Array.newInstance(fileAttributeClass, 0);

                Method m1 = filesClass.getMethod(
                        "createTempFile", String.class, String.class, attrs.getClass());
                Method m2 = filesClass.getMethod(
                        "createTempFile", pathClass, String.class, String.class, attrs.getClass());
                Method m3 = File.class.getMethod("toPath");
                Method m4 = pathClass.getMethod("toFile");

                createTempFile = m1;
                createTempFileInDirectory = m2;
                toPath = m3;
                toFile = m4;
                noFileAttributes = attrs;
            } catch (Exception e) {
                // Not available - fall back to java.io.File.createTempFile(...)
            }
        }
        FILES_CREATE_TEMP_FILE = createTempFile;
        FILES_CREATE_TEMP_FILE_IN_DIRECTORY = createTempFileInDirectory;
        FILE_TO_PATH = toPath;
        PATH_TO_FILE = toFile;
        NO_FILE_ATTRIBUTES = noFileAttributes;

        Method setReadable = null;
        try {
            setReadable = File.class.getMethod("setReadable", boolean.class, boolean.class);
        } catch (Exception e) {
            // Java 5 - java.io.File provides no way to change the permissions.
        }
        FILE_SET_READABLE = setReadable;
    }

    /**
     * Creates a new empty file in the specified directory, using the given
     * prefix and suffix strings to generate its name.
     * <p>
     * Unlike {@link File#createTempFile(String, String, File)}, which creates
     * the file with the default permissions of the process (usually readable
     * by every local user), the created file is only readable by its owner.
     * On Java 7+ the file is created via {@code java.nio.file.Files.createTempFile(...)}
     * which uses owner-only permissions, on older versions the permissions are
     * adjusted after the file was created.
     *
     * @param prefix    the prefix string to be used in generating the file's name
     * @param suffix    the suffix string to be used in generating the file's name
     * @param directory the directory in which the file is to be created, or
     *                  {@code null} if the default temporary-file directory is
     *                  to be used
     * @return the newly-created empty file
     * @throws IOException if the file could not be created or its permissions
     *                     could not be adjusted
     *
     * @see <a href="https://github.com/netty/netty/security/advisories/GHSA-5mcr-gq6c-3hq2">CVE-2021-21290</a>
     */
    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        if (FILES_CREATE_TEMP_FILE != null) {
            File file = createTempFileNio(prefix, suffix, directory);
            if (file != null) {
                return file;
            }
        }
        return createTempFileLegacy(prefix, suffix, directory);
    }

    /**
     * Creates the file via {@code java.nio.file.Files.createTempFile(...)}.
     *
     * @return the created file or {@code null} if the method could not be invoked
     */
    private static File createTempFileNio(String prefix, String suffix, File directory) throws IOException {
        try {
            Object path;
            if (directory == null) {
                path = FILES_CREATE_TEMP_FILE.invoke(null, prefix, suffix, NO_FILE_ATTRIBUTES);
            } else {
                path = FILES_CREATE_TEMP_FILE_IN_DIRECTORY.invoke(
                        null, FILE_TO_PATH.invoke(directory), prefix, suffix, NO_FILE_ATTRIBUTES);
            }
            return (File) PATH_TO_FILE.invoke(path);
        } catch (IllegalAccessException e) {
            // Should never happen as all the methods are public.
            return null;
        } catch (InvocationTargetException e) {
            throw toIOException(e.getCause());
        }
    }

    /**
     * Creates the file via {@link File#createTempFile(String, String, File)}
     * and restricts its permissions afterwards.
     */
    static File createTempFileLegacy(String prefix, String suffix, File directory) throws IOException {
        final File file;
        if (directory == null) {
            file = File.createTempFile(prefix, suffix);
        } else {
            file = File.createTempFile(prefix, suffix, directory);
        }

        // Try to adjust the perms, if this fails there is not much else we can do...
        restrictPermissions(file);
        return file;
    }

    /**
     * Makes the given file only readable by its owner.
     *
     * @throws IOException if the permissions could not be changed
     */
    static void restrictPermissions(File file) throws IOException {
        if (FILE_SET_READABLE == null) {
            // Java 5 - java.io.File provides no way to change the permissions.
            return;
        }
        if (!setReadable(file, false, false)) {
            throw new IOException("Failed to set permissions on temporary file " + file);
        }
        if (!setReadable(file, true, true)) {
            throw new IOException("Failed to set permissions on temporary file " + file);
        }
    }

    private static boolean setReadable(File file, boolean readable, boolean ownerOnly) throws IOException {
        try {
            return ((Boolean) FILE_SET_READABLE.invoke(
                    file, Boolean.valueOf(readable), Boolean.valueOf(ownerOnly))).booleanValue();
        } catch (IllegalAccessException e) {
            // Should never happen as the method is public.
            return false;
        } catch (InvocationTargetException e) {
            throw toIOException(e.getCause());
        }
    }

    private static IOException toIOException(Throwable cause) {
        if (cause instanceof IOException) {
            return (IOException) cause;
        }
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        IOException e = new IOException(String.valueOf(cause));
        e.initCause(cause);
        return e;
    }

    private PlatformDependent() {
        // Utility class
    }
}
