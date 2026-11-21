import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ParallelRunner {

    public enum Status {
        PASSED, FAILED
    }

    public record TestResult(String name, Status status, long durationMs) {
    }

    static Callable<TestResult> fakeTest(String name, long durationMs, Status status) {
        return () -> {
            Thread.sleep(durationMs);
            return new TestResult(name, status, durationMs);
        };
    }


    public void runSuite(List<Callable<TestResult>> tests) throws InterruptedException, ExecutionException {
        List<TestResult> results = new ArrayList<>();

        //Фиксируем время старта по точным системным часам
        long startNanos = System.nanoTime();

        //используем конструкцию try-with-resources, чтобы автоматически почистить память и точно закрыть пул
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            // Запускаем все 8 задач параллельно, invokeAll блокирует поток и ждёт завершения всех 8
            List<Future<TestResult>> futures = executor.invokeAll(tests);

            for (Future<TestResult> future : futures) {
                results.add(future.get());
            }
        }

        //Фиксируем время окончания и переводим наносекунды в миллисекунды
        long realDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        //Подсчитываем статистику (сколько прошло, сколько упало и сумму длительностей)
        long passedCount = results.stream()
                .filter(r -> r.status() == Status.PASSED)
                .count();
        long failedCount = results.size() - passedCount;     //Status.FAILED количество (раз статуса 2)

        //Время при последовательном выполнении тестов
        long totalSequentialMs = results.stream()
                .mapToLong(TestResult::durationMs)
                .sum();

        long savedMs = totalSequentialMs - realDurationMs;

        //Выводим итоговый отчёт
        System.out.printf("Прошло: %d, упало: %d%n", passedCount, failedCount);
        System.out.printf("Суммарное время тестов: %d ms%n", totalSequentialMs);
        System.out.printf("Реальное время прогона: %d ms%n", realDurationMs);
        System.out.printf("Параллельность сэкономила: %d ms%n", savedMs);
    }


    public static void main(String[] args) throws InterruptedException, ExecutionException {
        //проброс сбоев, если они вдруг случатся
        List<Callable<TestResult>> suite = List.of(
                fakeTest("LoginTest", 350, Status.PASSED),
                fakeTest("CreateTaskTest", 450, Status.PASSED),
                fakeTest("UpdateTaskTest", 400, Status.FAILED),
                fakeTest("DeleteTaskTest", 300, Status.PASSED),
                fakeTest("CommentTest", 500, Status.PASSED),
                fakeTest("SearchTest", 350, Status.FAILED),
                fakeTest("ProfileTest", 450, Status.PASSED),
                fakeTest("LogoutTest", 400, Status.PASSED)
        );
    // Набор из 8 тестов с суммарной длительностью 3200 мс

        new ParallelRunner().runSuite(suite);
    }
}