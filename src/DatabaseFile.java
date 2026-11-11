import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class DatabaseFile {
    private final Path path;

    public DatabaseFile(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public Database load() throws IOException {
        InputStream input;
        try {
            input = Files.newInputStream(path);
        } catch (NoSuchFileException error) {
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            throw error;
        }

        Object snapshot;
        try (InputStream file = input; ObjectInputStream stream = new ObjectInputStream(file)) {
            snapshot = stream.readObject();
        } catch (ClassNotFoundException | ObjectStreamException | EOFException error) {
            throw new IOException("Файл базы повреждён или имеет несовместимый формат", error);
        }
        if (!(snapshot instanceof Database)) {
            throw new IOException("В файле отсутствует снимок базы данных");
        }
        try {
            Database database = (Database) snapshot;
            return new Database(database.getTables());
        } catch (RuntimeException error) {
            throw new IOException("Снимок базы содержит некорректные таблицы или записи", error);
        }
    }

    public void save(Database database) throws IOException {
        if (database == null) {
            throw new IllegalArgumentException("Нужна база для сохранения");
        }
        Path temporary = Files.createTempFile(path.getParent(), ".database-", ".tmp");
        try {
            try (FileOutputStream file = new FileOutputStream(temporary.toFile());
                 ObjectOutputStream stream = new ObjectOutputStream(file)) {
                stream.writeObject(database);
                stream.flush();
                file.getFD().sync();
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }
}
