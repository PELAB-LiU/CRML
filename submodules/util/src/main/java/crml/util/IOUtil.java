package crml.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class IOUtil {

    /**
     * Reads the entire contents of a file as a UTF-8 string.
     *
     * @throws UncheckedIOException if an I/O error occurs
     */
    public static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @see #read(Path) */
    public static String read(File file) {
        return read(file.toPath());
    }

    /**
     * Concatenates each element via {@code toString()} and writes the result to a file using UTF-8
     * encoding, replacing any existing content. Null elements are written as empty strings.
     *
     * @return the string that was written
     * @throws UncheckedIOException if an I/O error occurs
     */
    public static String write(Path file, Object... contents) {
        StringBuilder sb = new StringBuilder();
        for (Object o : contents) {
            if (o != null) sb.append(o);
        }
        String text = sb.toString();
        try {
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return text;
    }

    /** @see #write(Path, Object...) */
    public static String write(File file, Object... contents) {
        return write(file.toPath(), (Object[]) contents);
    }

    /**
     * Returns the last component of the given path.
     * For directories, the path is normalized first to handle trailing separators.
     * Returns the full path string if the path is a root with no filename component.
     */
    public static String basename(Path file) {
        Path name = file.normalize().getFileName();
        return name == null ? file.toString() : name.toString();
    }
    
    /** @see #basename(Path) */
    public static String basename(File file) {
        return basename(file.toPath());
    }

    /**
     * Returns the last component of the given path with {@code suffix} removed if it ends with it.
     * Pass {@code ".*"} to strip any file extension.
     *
     * @see #strip(Path)
     */
    public static String strip(Path file, String suffix) {
        String name = basename(file);
        if (".*".equals(suffix)) {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    /** @see #strip(Path, String) */
    public static String strip(File file, String suffix) {
        return strip(file.toPath(), suffix);
    }

    /**
     * Returns the extension of the filename (without the leading dot), or {@code ""}  if there is none.
     * A leading dot (e.g. {@code .gitignore}) is not considered an extension.
     */
    public static String extension(Path file) {
        String name = basename(file);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    /** @see #extension(Path) */
    public static String extension(File file) {
        return extension(file.toPath());
    }

    /**
     * Returns the path with the last extension removed.
     * If the filename has no extension the path is returned unchanged.
     * A leading dot (e.g. {@code .gitignore}) is not considered an extension.
     */
    public static Path strip(Path file) {
        Path normalized = file.normalize();
        String name = basename(normalized);
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return file;
        String stem = name.substring(0, dot);
        Path parent = normalized.getParent();
        return parent == null ? file.getFileSystem().getPath(stem) : parent.resolve(stem);
    }

    /** @see #strip(Path) */
    public static File strip(File file) {
        return strip(file.toPath()).toFile();
    }

    /**
     * Returns the path with {@code ext} appended as an extension.
     * A leading dot in {@code ext} is optional — {@code "java"} and {@code ".java"} are equivalent.
     */
    public static Path addExtension(Path file, String ext) {
        String dot = ext.startsWith(".") ? "" : ".";
        Path normalized = file.normalize();
        Path parent = normalized.getParent();
        String newName = basename(normalized) + dot + ext;
        return parent == null ? file.getFileSystem().getPath(newName) : parent.resolve(newName);
    }

    /** @see #addExtension(Path, String) */
    public static File addExtension(File file, String ext) {
        return addExtension(file.toPath(), ext).toFile();
    }
}
