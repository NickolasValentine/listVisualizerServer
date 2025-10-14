package data;

import java.util.*;

public class UserFactory {
    private final ArrayList<UserType> builders;

    public UserFactory() {
        builders = new ArrayList<>();
        builders.add(new IntegerType());
        builders.add(new DoubleType());
        builders.add(new StringType());
        builders.add(new FractionType());
    }
    // Для выпадающего списка в GUI
    public ArrayList<String> getTypeNameList() {
        ArrayList<String> names = new ArrayList<>();
        for (UserType u : builders) names.add(u.typeName());
        return names;
    }
    // Для получения прототипа по имени
    public UserType getBuilderByName(String name) {
        for (UserType u : builders) if (u.typeName().equals(name)) return u;
        return null;
    }

    public ArrayList<UserType> getAllBuilders() { return builders; }
}
