import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlParser {
    private enum Kind { WORD, NUMBER, STRING, SYMBOL, END }

    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private final String query;
    private final Database database;
    private int offset;
    private Token token;

    private SqlParser(String query, Database database) {
        this.query = query;
        this.database = database;
        advance();
    }

    public static SqlCommand parse(String query, Database database) {
        if (query == null || database == null) {
            throw new IllegalArgumentException("Для разбора нужны запрос и схема базы");
        }
        return new SqlParser(query, database).readCommand();
    }

    private SqlCommand readCommand() {
        SqlCommand command;
        if (takeWord("INSERT")) {
            command = readInsert();
        } else if (takeWord("SELECT")) {
            command = readSelect();
        } else if (takeWord("UPDATE")) {
            command = readUpdate();
        } else if (takeWord("DELETE")) {
            expectWord("FROM");
            Table table = readTable();
            command = new SqlCommand.Delete(table.getName(), readCondition(table));
        } else {
            throw error(token.position, "ожидалась команда INSERT, SELECT, UPDATE или DELETE");
        }
        expectSymbol(";");
        if (token.kind != Kind.END) {
            throw error(token.position, "после точки с запятой ожидался конец запроса");
        }
        return command;
    }

    private SqlCommand.Insert readInsert() {
        expectWord("INTO");
        Table table = readTable();
        List<Integer> indexes = new ArrayList<Integer>();
        if (takeSymbol("(")) {
            indexes = resolveColumns(table, readNames());
            expectSymbol(")");
        } else {
            for (int index = 0; index < table.getColumns().size(); index++) {
                indexes.add(index);
            }
        }
        expectWord("VALUES");
        List<Row> rows = new ArrayList<Row>();
        do {
            rows.add(readRow(table, indexes));
        } while (takeSymbol(","));
        return new SqlCommand.Insert(table.getName(), rows);
    }

    private Row readRow(Table table, List<Integer> indexes) {
        int start = token.position;
        expectSymbol("(");
        List<Column> columns = table.getColumns();
        List<Object> values = new ArrayList<Object>(Collections.nCopies(columns.size(), (Object) null));
        int count = 0;
        do {
            if (count == indexes.size()) {
                throw error(token.position, "значений больше, чем столбцов");
            }
            int index = indexes.get(count++);
            values.set(index, readValue(columns.get(index)));
        } while (takeSymbol(","));
        expectSymbol(")");
        if (count != indexes.size()) {
            throw error(start, "значений меньше, чем столбцов");
        }
        try {
            return table.normalizeRow(new Row(values));
        } catch (IllegalArgumentException exception) {
            throw error(start, exception.getMessage());
        }
    }

    private SqlCommand.Select readSelect() {
        boolean allColumns = takeSymbol("*");
        List<Token> names = allColumns ? Collections.<Token>emptyList() : readNames();
        expectWord("FROM");
        Table table = readTable();
        List<String> columns = new ArrayList<String>();
        if (allColumns) {
            for (Column column : table.getColumns()) {
                columns.add(column.getName());
            }
        } else {
            for (int index : resolveColumns(table, names)) {
                columns.add(table.getColumns().get(index).getName());
            }
        }
        return new SqlCommand.Select(table.getName(), columns, readCondition(table));
    }

    private SqlCommand.Update readUpdate() {
        Table table = readTable();
        expectWord("SET");
        Token name = expectName();
        Column column = table.getColumns().get(columnIndex(table, name));
        expectSymbol("=");
        int position = token.position;
        Object value = readValue(column);
        if (value == null && column.isRequired()) {
            throw error(position, "столбец " + column.getName() + " обязателен");
        }
        return new SqlCommand.Update(table.getName(), column.getName(), value, readCondition(table));
    }

    private Condition readCondition(Table table) {
        if (!takeWord("WHERE")) {
            return null;
        }
        Column column = table.getColumns().get(columnIndex(table, expectName()));
        int position = token.position;
        Condition.Operator operator = readOperator();
        Condition condition = new Condition(column.getName(), operator, readValue(column));
        try {
            condition.normalizeValue(column);
        } catch (IllegalArgumentException exception) {
            throw error(position, exception.getMessage());
        }
        return condition;
    }

    private Condition.Operator readOperator() {
        if (takeSymbol("=")) return Condition.Operator.EQUAL;
        if (takeSymbol("!=")) return Condition.Operator.NOT_EQUAL;
        if (takeSymbol("<")) return Condition.Operator.LESS;
        if (takeSymbol(">")) return Condition.Operator.GREATER;
        if (takeSymbol("<=")) return Condition.Operator.LESS_OR_EQUAL;
        if (takeSymbol(">=")) return Condition.Operator.GREATER_OR_EQUAL;
        throw error(token.position, "ожидался оператор сравнения");
    }

    private Object readValue(Column column) {
        if (takeWord("NULL")) {
            return null;
        }
        Object value;
        try {
            value = column.getType().normalize(readLiteral(column.getType()), column.getName());
        } catch (NumberFormatException exception) {
            throw error(token.position, "недопустимое число для столбца " + column.getName());
        } catch (IllegalArgumentException exception) {
            throw error(token.position, exception.getMessage());
        }
        advance();
        return value;
    }

    private Object readLiteral(DataType type) {
        switch (type) {
            case INTEGER:
                if (token.kind == Kind.NUMBER) return Integer.valueOf(token.text);
                break;
            case DOUBLE:
                if (token.kind == Kind.NUMBER) return Double.valueOf(token.text);
                break;
            case STRING:
                if (token.kind == Kind.STRING) return token.text;
                break;
            case DATE:
                if (token.kind == Kind.STRING) return parseDate(token.text);
                break;
            case BOOLEAN:
                if (isWord("TRUE")) return Boolean.TRUE;
                if (isWord("FALSE")) return Boolean.FALSE;
                break;
            default:
                throw new AssertionError(type);
        }
        throw new IllegalArgumentException("ожидалось значение типа " + type);
    }

    private Date parseDate(String value) {
        if (!DATE.matcher(value).matches()) {
            throw new IllegalArgumentException("ожидалась дата в формате ГГГГ-ММ-ДД");
        }
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        calendar.setGregorianChange(new Date(Long.MIN_VALUE));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setCalendar(calendar);
        format.setLenient(false);
        try {
            return format.parse(value);
        } catch (ParseException exception) {
            throw new IllegalArgumentException("некорректная дата " + value);
        }
    }

    private Table readTable() {
        Token name = expectName();
        try {
            return database.getTable(name.text);
        } catch (IllegalArgumentException exception) {
            throw error(name.position, exception.getMessage());
        }
    }

    private List<Token> readNames() {
        List<Token> names = new ArrayList<Token>();
        do {
            names.add(expectName());
        } while (takeSymbol(","));
        return names;
    }

    private List<Integer> resolveColumns(Table table, List<Token> names) {
        List<Integer> indexes = new ArrayList<Integer>();
        for (Token name : names) {
            int index = columnIndex(table, name);
            if (indexes.contains(index)) {
                throw error(name.position, "повторный столбец " + name.text);
            }
            indexes.add(index);
        }
        return indexes;
    }

    private int columnIndex(Table table, Token name) {
        try {
            return table.columnIndex(name.text);
        } catch (IllegalArgumentException exception) {
            throw error(name.position, exception.getMessage());
        }
    }

    private Token expectName() {
        if (token.kind != Kind.WORD) {
            throw error(token.position, "ожидалось имя таблицы или столбца");
        }
        Token name = token;
        advance();
        return name;
    }

    private boolean isWord(String word) {
        return token.kind == Kind.WORD && token.text.equalsIgnoreCase(word);
    }

    private boolean takeWord(String word) {
        if (!isWord(word)) return false;
        advance();
        return true;
    }

    private void expectWord(String word) {
        if (!takeWord(word)) {
            throw error(token.position, "ожидалось " + word);
        }
    }

    private boolean takeSymbol(String symbol) {
        if (token.kind != Kind.SYMBOL || !token.text.equals(symbol)) return false;
        advance();
        return true;
    }

    private void expectSymbol(String symbol) {
        if (!takeSymbol(symbol)) {
            throw error(token.position, "ожидался символ " + symbol);
        }
    }

    private void advance() {
        while (offset < query.length() && Character.isWhitespace(query.charAt(offset))) {
            offset++;
        }
        int start = offset;
        if (offset == query.length()) {
            token = new Token(Kind.END, "", start);
            return;
        }
        char current = query.charAt(offset);
        if (current == '\'') {
            token = readString(start);
        } else if (isNameStart(current)) {
            offset++;
            while (offset < query.length()) {
                char next = query.charAt(offset);
                if (!isNameStart(next) && !isDigit(next)) break;
                offset++;
            }
            token = new Token(Kind.WORD, query.substring(start, offset), start);
        } else if (isDigit(current) || current == '+' || current == '-' || current == '.') {
            Matcher number = NUMBER.matcher(query).region(offset, query.length());
            if (!number.lookingAt()) {
                throw error(start, "некорректная запись числа");
            }
            offset = number.end();
            token = new Token(Kind.NUMBER, number.group(), start);
        } else if ("(),*;=<>!".indexOf(current) >= 0) {
            offset++;
            if ("<>!".indexOf(current) >= 0 && offset < query.length() && query.charAt(offset) == '=') {
                offset++;
            }
            token = new Token(Kind.SYMBOL, query.substring(start, offset), start);
        } else {
            throw error(start, "неизвестный символ " + current);
        }
    }

    private Token readString(int start) {
        offset++;
        StringBuilder value = new StringBuilder();
        while (offset < query.length()) {
            char current = query.charAt(offset++);
            if (current == '\'') {
                if (offset == query.length() || query.charAt(offset) != '\'') {
                    return new Token(Kind.STRING, value.toString(), start);
                }
                offset++;
            }
            value.append(current);
        }
        throw error(start, "строка без закрывающей кавычки");
    }

    private static boolean isNameStart(char value) {
        return value == '_' || (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static IllegalArgumentException error(int position, String message) {
        return new IllegalArgumentException("Позиция " + (position + 1) + ": " + message);
    }

    private static final class Token {
        private final Kind kind;
        private final String text;
        private final int position;

        private Token(Kind kind, String text, int position) {
            this.kind = kind;
            this.text = text;
            this.position = position;
        }
    }
}
