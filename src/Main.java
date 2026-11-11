import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

            if (args.length == 1 && isEmpty(database)) {
                runDemo(database, storage);
            } else {
                if (args.length == 1) {
                    System.out.println("Демонстрация с записью доступна для пустой базы.");
                }
                for (Table table : database.getTables()) {
                    System.out.println("Таблица: " + table.getName());
                    printRows(table, table.getRows());
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

    private static void runDemo(Database database, DatabaseFile storage) throws IOException {
        database.insert("brands", new Row(Arrays.<Object>asList(1, "Toyota")));
        storage.save(database);
        database.insert("cars", new Row(Arrays.<Object>asList(
                1, 1, "Camry", 25000.0, new Date(0), true)));
        storage.save(database);

        Map<String, Object> corolla = new HashMap<String, Object>();
        corolla.put("id", 2);
        corolla.put("brand_id", 1);
        corolla.put("model", "Corolla");
        database.insert("cars", corolla);
        storage.save(database);

        Condition corollaId = new Condition("id", Condition.Operator.EQUAL, 2);
        int updated = database.update("cars", "price", 30000, corollaId);
        storage.save(database);
        System.out.println("Обновлено записей: " + updated);
        Condition expensive = new Condition("price", Condition.Operator.GREATER, 20000);
        System.out.println("Автомобили дороже 20000:");
        printRows(database.getTable("cars"), database.select("cars", expensive));

        Condition camryId = new Condition("id", Condition.Operator.EQUAL, 1);
        int deleted = database.delete("cars", camryId);
        storage.save(database);
        System.out.println("Удалено записей: " + deleted);
        System.out.println("Автомобили после удаления:");
        printRows(database.getTable("cars"), database.select("cars", null));
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

    private static void printRows(Table table, Iterable<Row> rows) {
        for (Column column : table.getColumns()) {
            System.out.print(column.getName() + "\t");
        }
        System.out.println();
        for (Row row : rows) {
            for (Object value : row.getValues()) {
                System.out.print(value + "\t");
            }
            System.out.println();
        }
        System.out.println();
    }
}
