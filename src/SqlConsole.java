import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public final class SqlConsole {
    private final DatabaseFile storage;
    private final SimpleDateFormat dateFormat = SqlParser.newDateFormat();
    private Database database;

    public SqlConsole(Database database, DatabaseFile storage) {
        this.database = database;
        this.storage = storage;
    }

    public void run() {
        System.out.println("Вводите по одному SQL-запросу на строку. Для выхода введите EXIT.");
        try (Scanner input = new Scanner(System.in)) {
            while (true) {
                System.out.print("sql> ");
                System.out.flush();
                if (!input.hasNextLine()) {
                    break;
                }
                String query = input.nextLine();
                String trimmed = query.trim();
                if ("EXIT".equalsIgnoreCase(trimmed) || "EXIT;".equalsIgnoreCase(trimmed)) {
                    break;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    execute(query);
                } catch (IllegalArgumentException error) {
                    System.err.println("Ошибка запроса. " + error.getMessage());
                } catch (IOException error) {
                    System.err.println("Не удалось сохранить изменения. " + error.getMessage());
                }
            }
        }
    }

    void execute(String query) throws IOException {
        SqlCommand command = SqlParser.parse(query, database);
        if (command instanceof SqlCommand.Select) {
            printSelect((SqlCommand.Select) command);
            return;
        }

        Database updated = new Database(database.getTables());
        String action;
        int count;
        String table = command.getTableName();
        if (command instanceof SqlCommand.Insert) {
            count = updated.insertAll(table, ((SqlCommand.Insert) command).getRows());
            action = "Добавлено";
        } else if (command instanceof SqlCommand.Update) {
            SqlCommand.Update update = (SqlCommand.Update) command;
            count = updated.update(table, update.getColumn(), update.getValue(), update.getCondition());
            action = "Обновлено";
        } else if (command instanceof SqlCommand.Delete) {
            count = updated.delete(table, ((SqlCommand.Delete) command).getCondition());
            action = "Удалено";
        } else {
            throw new IllegalArgumentException("Неизвестная команда");
        }

        if (count > 0) {
            storage.save(updated);
            database = updated;
        }
        System.out.println(action + " записей: " + count);
    }

    private void printSelect(SqlCommand.Select select) {
        Table table = database.getTable(select.getTableName());
        List<String> headers = select.getColumns();
        int[] indexes = new int[headers.size()];
        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            indexes[i] = table.columnIndex(headers.get(i));
            widths[i] = headers.get(i).length();
        }

        List<Row> selected = database.select(table.getName(), select.getCondition());
        List<List<String>> cells = new ArrayList<List<String>>();
        for (Row row : selected) {
            List<String> line = new ArrayList<String>();
            for (int i = 0; i < indexes.length; i++) {
                String value = formatValue(row.getValue(indexes[i]));
                line.add(value);
                widths[i] = Math.max(widths[i], value.length());
            }
            cells.add(line);
        }

        StringBuilder border = new StringBuilder("+");
        for (int width : widths) {
            appendRepeated(border, '-', width + 2);
            border.append('+');
        }
        System.out.println(border);
        printLine(headers, widths);
        System.out.println(border);
        for (List<String> line : cells) {
            printLine(line, widths);
        }
        if (!cells.isEmpty()) {
            System.out.println(border);
        }
        System.out.println("Найдено записей: " + selected.size());
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Date) {
            return dateFormat.format((Date) value);
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "TRUE" : "FALSE";
        }
        return value.toString().replace("\\", "\\\\")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
                .replace("|", "\\|");
    }

    private void printLine(List<String> cells, int[] widths) {
        StringBuilder line = new StringBuilder("|");
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            line.append(' ').append(cell);
            appendRepeated(line, ' ', widths[i] - cell.length() + 1);
            line.append('|');
        }
        System.out.println(line);
    }

    private void appendRepeated(StringBuilder line, char symbol, int count) {
        for (int i = 0; i < count; i++) {
            line.append(symbol);
        }
    }
}
