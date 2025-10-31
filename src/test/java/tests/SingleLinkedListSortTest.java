package tests;

import data.*;
import list.SingleLinkedList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SingleLinkedListSortTest {

    private SingleLinkedList createIntegerList(Integer... values) {
        SingleLinkedList list = new SingleLinkedList(new IntegerType());
        for (Integer v : values) {
            list.add(v);
        }
        return list;
    }

    private void assertListEquals(SingleLinkedList list, Integer... expected) {
        assertEquals(expected.length, list.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], list.get(i));
        }
    }

    // == Структурное тестирование ==

    // size <= 1 (пустой) выход без сортировки
    @Test
    void testEmptyList() {
        var list = createIntegerList();
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list);
    }

    // size <= 1 (один элемент) выход без сортировки
    @Test
    void testSingleElement() {
        var list = createIntegerList(42);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 42);
    }

    // Два элемента должен поменять местами.
    @Test
    void testTwoElementsUnsorted() {
        var list = createIntegerList(3, 1);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 1, 3);
    }

    // Худший случай quicksort: уже отсортированный
    @Test
    void testAlreadySorted() {
        var list = createIntegerList(1, 2, 3, 4, 5);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 1, 2, 3, 4, 5);
    }
    // Элементы в обратном порядке проверяет рекурсию
    @Test
    void testReverseSorted() {
        var list = createIntegerList(5, 4, 3, 2, 1);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 1, 2, 3, 4, 5);
    }

    // Все элементы одинаковые все сравнения = 0
    @Test
    void testAllEqual() {
        var list = createIntegerList(7, 7, 7, 7);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 7, 7, 7, 7);
    }

    // Есть одинаковые
    @Test
    void testDuplicatesMixed() {
        var list = createIntegerList(3, 1, 4, 1, 5, 9, 2, 6, 5);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 1, 1, 2, 3, 4, 5, 5, 6, 9);
    }

    // == Функциональное тестирование ==


    // Экстремальное значение находится в середине набора
    @Test
    void testExtremumInMiddle() {
        var list = createIntegerList(2, 1, 5, 3, 4); // max=5 in middle
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 1, 2, 3, 4, 5);
    }

    // Экстремальное значение находится в начале набора
    @Test
    void testExtremumAtStart() {
        var list = createIntegerList(10, 1, 2, 3);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 1, 2, 3, 10);
    }

    // Экстремальное значение находится в конце набора
    @Test
    void testExtremumAtEnd() {
        var list = createIntegerList(1, 2, 3, 10);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 1, 2, 3, 10);
    }

    // В наборе несколько совпадающих экстремальных значений
    @Test
    void testMultipleMaxValues() {
        var list = createIntegerList(5, 1, 5, 2, 5);
        list.sort(new IntegerType().getTypeComparator());
        assertListEquals(list, 1, 2, 5, 5, 5);
    }

    // == Тестирование полупрозрачного ящика ==

    // Тест для Fraction с переполнением
    @Test
    void testFractionSortWithLargeValues() {
        var list = new SingleLinkedList(new FractionType());
        // Эти дроби вызовут переполнение при сравнении через long
        list.add(new Fraction(0, Long.MAX_VALUE / 2, 1));
        list.add(new Fraction(0, Long.MAX_VALUE / 3, 1));
        list.sort(new FractionType().getTypeComparator());
        // Должно отсортироваться корректно через double-fallback
        var first = (Fraction) list.get(0);
        var second = (Fraction) list.get(1);
        assertTrue(first.toDouble() <= second.toDouble());
    }
}