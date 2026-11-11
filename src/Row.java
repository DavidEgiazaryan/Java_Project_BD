import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class Row implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<Object> values;

    public Row(List<Object> values) {
        if (values == null) {
            throw new IllegalArgumentException("Список значений обязателен");
        }
        this.values = copyValues(values);
    }

    public List<Object> getValues() {
        return Collections.unmodifiableList(copyValues(values));
    }

    public Object getValue(int index) {
        return copyValue(values.get(index));
    }

    static Object copyValue(Object value) {
        return value instanceof Date ? new Date(((Date) value).getTime()) : value;
    }

    private static List<Object> copyValues(List<Object> source) {
        List<Object> copy = new ArrayList<Object>();
        for (Object value : source) {
            copy.add(copyValue(value));
        }
        return copy;
    }
}
