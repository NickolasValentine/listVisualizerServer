package tests;

import data.IntegerType;
import data.Comparator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import list.SingleLinkedList;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

public class SingleLinkedListSortBenchmarkTest {

    private static final int MIN_N = 2_000;
    private static final int MAX_N = 100_000;
    private static final int POINTS = 50;
    private static final int WARMUP_ROUNDS = 3;   // прогрев
    private static final int MEASURE_ROUNDS = 5;  // замеры на точку

    private static int[] NS;

    @BeforeAll
    static void initSizes() {
        NS = new int[POINTS];
        for (int i = 0; i < POINTS; i++) {
            NS[i] = MIN_N + (MAX_N - MIN_N) * i / (POINTS - 1);
        }
    }

    // Вспомогательная функция: тихая пауза
    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // Создаёт список из n случайных целых
    private SingleLinkedList createRandomList(int n) {
        var list = new SingleLinkedList(new IntegerType());
        var rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            list.add(rnd.nextInt());
        }
        return list;
    }

    // Проверяет, что список отсортирован неубывающе
    private void assertNonDecreasing(SingleLinkedList list) {
        for (int i = 0; i < list.size() - 1; i++) {
            Integer a = (Integer) list.get(i);
            Integer b = (Integer) list.get(i + 1);
            assertTrue(a <= b, "Нарушение порядка на позиции " + i);
        }
    }

    @Test
    void runSortBenchmark(@TempDir Path tempDir) throws IOException {
        // Путь к CSV
        File perfDir = new File("target/perf");
        if (!perfDir.exists()) perfDir.mkdirs();
        File csvFile = new File(perfDir, "sort_bench_summary.csv");

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(csvFile)))) {
            // Заголовок
            pw.println("size,avg_time_ms,std_time_ms,avg_mem_delta_mb,nlog2n,n");

            Comparator comp = new IntegerType().getTypeComparator();

            for (int n : NS) {
                System.out.printf("Замер для n = %d...%n", n);

                // Прогрев
                for (int r = 0; r < WARMUP_ROUNDS; r++) {
                    var list = createRandomList(n);
                    list.sort(comp);
                }

                // Замеры
                double[] timesMs = new double[MEASURE_ROUNDS];
                double[] memDeltasMb = new double[MEASURE_ROUNDS];

                for (int r = 0; r < MEASURE_ROUNDS; r++) {
                    // Принудительная GC + пауза
                    System.gc();
                    sleepQuiet(60);


                    // Замер памяти до
                    long memBefore = getUsedMemoryBytes();

                    // Создаём список ДО замераы
                    var list = createRandomList(n);



                    // Замер времени
                    long t0 = System.nanoTime();
                    list.sort(comp);
                    long t1 = System.nanoTime();

                    // Замер памяти после
                    long memAfter = getUsedMemoryBytes();

                    // Проверка корректности
                    assertEquals(n, list.size());
                    assertNonDecreasing(list);

                    timesMs[r] = (t1 - t0) / 1_000_000.0;
                    double memDeltaMb = Math.max(0, (memAfter - memBefore)) / (1024.0 * 1024.0);
                    memDeltasMb[r] = memDeltaMb;
                }

                // Статистика
                double avgTime = Arrays.stream(timesMs).average().orElse(0);
                double stdTime = calculateStdDev(timesMs, avgTime);
                double avgMem = Arrays.stream(memDeltasMb).average().orElse(0);
                double nlog2n = n * (Math.log(n) / Math.log(2));

                // Запись в CSV
                pw.printf(Locale.US, "%d,%.4f,%.4f,%.4f,%.2f,%d%n",
                        n, avgTime, stdTime, avgMem, nlog2n, n);
                pw.flush();
            }
        }

        System.out.println("Результаты сохранены в: " + csvFile.getAbsolutePath());
    }

    // Возвращает объём используемой кучи в МБ
    private long getUsedMemoryMB() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    // Возвращает объём используемой кучи в БАЙТАХ
    private long getUsedMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    // Стандартное отклонение
    private double calculateStdDev(double[] values, double mean) {
        double sum = 0;
        for (double v : values) {
            double diff = v - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / values.length);
    }
}