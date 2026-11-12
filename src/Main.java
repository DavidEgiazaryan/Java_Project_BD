import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

public class Main {
    private static final String DATABASE_FILE = "database.ser";

    public static void main(String[] args) {
        if (args.length == 2 && "--parse".equals(args[0])) {
            checkQuery(args[1]);
            return;
        }
        if (args.length > 1 || (args.length == 1 && !"--demo".equals(args[0]))) {
            System.err.println("Использование: Main [--demo] или Main --parse \"запрос;\"");
            System.exit(2);
        }

        Path path = Paths.get(DATABASE_FILE).toAbsolutePath().normalize();
        DatabaseFile storage = new DatabaseFile(path);
        try {
            Database database = storage.load();
            if (database == null) {
                database = createEmptyDatabase();
                storage.save(database);
                System.out.println("Создана пустая база: " + path);
            } else {
                System.out.println("Загружена база: " + path);
            }

            SqlConsole console = new SqlConsole(database, storage);
            if (args.length == 0) {
                console.run();
            } else if (isEmpty(database)) {
                runDemo(console);
            } else {
                System.out.println("Демонстрация с записью доступна для пустой базы.");
                for (Table table : database.getTables()) {
                    System.out.println("Таблица: " + table.getName());
                    console.execute("SELECT * FROM " + table.getName() + ";");
                }
            }
        } catch (IOException error) {
            System.err.println("Ошибка работы с файлом " + path + ": " + error.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException error) {
            System.err.println("Ошибка демонстрации: " + error.getMessage());
            System.exit(1);
        }
    }

    private static void checkQuery(String query) {
        try {
            SqlCommand command = SqlParser.parse(query, createEmptyDatabase());
            System.out.println("Запрос к таблице " + command.getTableName() + " разобран.");
        } catch (IllegalArgumentException error) {
            System.err.println("Ошибка запроса. " + error.getMessage());
            System.exit(1);
        }
    }

    private static boolean isEmpty(Database database) {
        for (Table table : database.getTables()) {
            if (!table.getRows().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void runDemo(SqlConsole console) throws IOException {
        console.execute("INSERT INTO brands VALUES (1, 'Toyota');");
        console.execute("INSERT INTO cars VALUES (1, 1, 'Camry', 25000.0, '2024-05-17', TRUE);");
        console.execute("INSERT INTO cars (id, brand_id, model) VALUES (2, 1, 'Corolla');");
        console.execute("UPDATE cars SET price = 30000 WHERE id = 2;");
        System.out.println("Автомобили дороже 20000:");
        console.execute("SELECT * FROM cars WHERE price > 20000;");
        console.execute("DELETE FROM cars WHERE id = 1;");
        System.out.println("Автомобили после удаления:");
        console.execute("SELECT * FROM cars;");
    }

    static Database createEmptyDatabase() {
        Table brands = new Table("brands", Arrays.asList(
                Column.primaryKey("id"),
                new Column("name", DataType.STRING, false)
        ), Collections.<Row>emptyList());

        Table cars = new Table("cars", Arrays.asList(
                Column.primaryKey("id"),
                Column.foreignKey("brand_id", "brands"),
                new Column("model", DataType.STRING, false),
                new Column("price", DataType.DOUBLE, false),
                new Column("production_date", DataType.DATE, false),
                new Column("available", DataType.BOOLEAN, false)
        ), Collections.<Row>emptyList());

        return new Database(Arrays.asList(brands, cars));
    }
}
