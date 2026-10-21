import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SeatBookingApplication {

    private static final String DEFAULT_EVENT_NAME = "Tech Meetup 2026";
    private static final int DEFAULT_TOTAL_SEATS = 10;
    private static final int THREAD_POOL_SIZE = 3;
    private static final long MONITOR_INTERVAL_MILLIS = 5000L;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10L;
    private static final long FIRST_STATUS_TIMEOUT_SECONDS = 2L;
    private static final int EXIT_OPTION = 5;

    // main holds this for the whole prompt and the monitor takes it before
    // printing, so a status line never breaks a line the user is typing
    private static final Object CONSOLE_LOCK = new Object();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        printBanner();

        String eventName = readLine(scanner, "Enter Event Name: ", DEFAULT_EVENT_NAME);
        if (eventName == null) {
            return;
        }

        Integer totalSeats = promptPositiveInt(scanner, "Enter Total Number of Seats: ",
                DEFAULT_TOTAL_SEATS);
        if (totalSeats == null) {
            return;
        }

        Event event = new Event(eventName, totalSeats.intValue());
        BookingService service = new BookingService(event);
        println(String.format("[OK] Created Event '%s' with %d seats (Seats 1 to %d).",
                event.getEventName(), event.getTotalSeats(), event.getTotalSeats()));

        // monitor runs on its own thread, the executor runs the bookings
        CountDownLatch firstStatus = new CountDownLatch(1);
        StatusMonitor monitor = new StatusMonitor(MONITOR_INTERVAL_MILLIS, CONSOLE_LOCK, firstStatus);
        Thread monitorThread = new Thread(monitor, "status-monitor");
        monitorThread.start();

        // wait for the first status line so it shows up before the menu
        try {
            firstStatus.await(FIRST_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        boolean exitRequested = false;
        try {
            while (!exitRequested) {
                // one lock for the whole menu turn, the monitor waits until we are done
                synchronized (CONSOLE_LOCK) {
                    printMenu();

                    Integer choice = promptMenuChoice(scanner);
                    if (choice == null) {
                        println("[APPLICATION] Input closed; exiting.");
                        break;
                    }

                    switch (choice.intValue()) {
                        case 1 -> {
                            String name = readRequired(scanner, "Customer Name: ");
                            if (name == null) {
                                exitRequested = true;
                                break;
                            }
                            Integer seat = promptInt(scanner, "Seat Number: ");
                            if (seat == null) {
                                exitRequested = true;
                                break;
                            }

                            List<Future<?>> futures = new ArrayList<>();
                            futures.add(executor.submit(new BookingTask(service, name, seat.intValue())));
                            waitForCompletion(futures);
                        }
                        case 2 -> {
                            Integer count = promptPositiveInt(scanner, "How many concurrent booking requests? ", 1);
                            if (count == null) {
                                exitRequested = true;
                                break;
                            }

                            List<BookingTask> tasks = new ArrayList<>();
                            boolean inputClosed = false;
                            for (int i = 1; i <= count.intValue(); i++) {
                                String name = readRequired(scanner, "  Request " + i + " - Customer Name: ");
                                if (name == null) {
                                    inputClosed = true;
                                    break;
                                }
                                Integer seat = promptInt(scanner, "  Request " + i + " - Seat Number: ");
                                if (seat == null) {
                                    inputClosed = true;
                                    break;
                                }
                                tasks.add(new BookingTask(service, name, seat.intValue()));
                            }
                            if (inputClosed) {
                                exitRequested = true;
                                break;
                            }

                            println("[APPLICATION] Launching " + count.intValue()
                                    + " requests SIMULTANEOUSLY across thread pool...");

                            List<Future<?>> futures = new ArrayList<>();
                            for (BookingTask task : tasks) {
                                futures.add(executor.submit(task));
                            }
                            waitForCompletion(futures);
                        }
                        case 3 -> println("[AVAILABLE SEATS]: " + service.getAvailableSeats());
                        case 4 -> {
                            List<Booking> bookings = service.getBookings();
                            println("[CONFIRMED BOOKINGS]:");
                            if (bookings.isEmpty()) {
                                println("  (no bookings yet)");
                            } else {
                                for (Booking booking : bookings) {
                                    println("  - " + booking);
                                }
                            }
                        }
                        case EXIT_OPTION -> exitRequested = true;
                        default -> println("[ERROR] Invalid option. Please enter a number between 1 and 5.");
                    }
                }
            }
        } catch (NoSuchElementException e) {
            println("[APPLICATION] Input closed; exiting.");
        }

        synchronized (CONSOLE_LOCK) {
            println("[APPLICATION] Shutting down thread pool executor...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    println("[APPLICATION] Executor did not finish in time; forcing shutdown.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }

            println("[APPLICATION] Stopping background status monitor...");
        }

        // join without the lock, the monitor needs it to print the last line
        monitor.stopMonitoring();
        try {
            monitorThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (CONSOLE_LOCK) {
            println("==================== BOOKING SUMMARY ====================");
            service.displaySummary();
            println("=========================================================");
        }

        scanner.close();
    }

    private static void println(String message) {
        synchronized (CONSOLE_LOCK) {
            System.out.println(message);
        }
    }

    private static void printBanner() {
        synchronized (CONSOLE_LOCK) {
            System.out.println("=================================================================");
            System.out.println("        CONCURRENT EVENT SEAT BOOKING SYSTEM (INTERACTIVE)       ");
            System.out.println("=================================================================");
            System.out.println();
        }
    }

    private static void printMenu() {
        synchronized (CONSOLE_LOCK) {
            System.out.println("--- MENU ---");
            System.out.println("1. Book a Single Seat");
            System.out.println("2. Submit Multiple Concurrent Bookings");
            System.out.println("3. View Available Seats");
            System.out.println("4. View Confirmed Bookings");
            System.out.println("5. Exit");
        }
    }

    // returns null when the input is closed
    private static String readLine(Scanner scanner, String prompt, String fallback) {
        synchronized (CONSOLE_LOCK) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                return null;
            }
            String input = scanner.nextLine().trim();
            return input.isEmpty() ? fallback : input;
        }
    }

    // keeps asking until the user types something
    private static String readRequired(Scanner scanner, String prompt) {
        while (true) {
            synchronized (CONSOLE_LOCK) {
                System.out.print(prompt);
                if (!scanner.hasNextLine()) {
                    return null;
                }
                String input = scanner.nextLine().trim();
                if (!input.isEmpty()) {
                    return input;
                }
            }
            println("[ERROR] This field cannot be empty.");
        }
    }

    private static Integer promptMenuChoice(Scanner scanner) {
        while (true) {
            Integer choice = promptInt(scanner, "Select an option (1-5): ");
            if (choice == null) {
                return null;
            }
            if (choice.intValue() >= 1 && choice.intValue() <= EXIT_OPTION) {
                return choice;
            }
            println("[ERROR] Invalid option. Please enter a number between 1 and 5.");
        }
    }

    private static Integer promptPositiveInt(Scanner scanner, String prompt, int fallback) {
        while (true) {
            String input = readLine(scanner, prompt, "");
            if (input == null) {
                return null;
            }
            if (input.isEmpty()) {
                return Integer.valueOf(fallback);
            }
            try {
                int value = Integer.parseInt(input);
                if (value > 0) {
                    return Integer.valueOf(value);
                }
                println("[ERROR] Value must be greater than 0.");
            } catch (NumberFormatException e) {
                println("[ERROR] Please enter a whole number.");
            }
        }
    }

    // seat numbers outside the range are allowed here, the service rejects them
    private static Integer promptInt(Scanner scanner, String prompt) {
        while (true) {
            String input = readLine(scanner, prompt, "");
            if (input == null) {
                return null;
            }
            try {
                return Integer.valueOf(Integer.parseInt(input));
            } catch (NumberFormatException e) {
                println("[ERROR] Please enter a whole number (maximum " + Integer.MAX_VALUE + ").");
            }
        }
    }

    // wait for the tasks so the menu shows the final state
    private static void waitForCompletion(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                println("[APPLICATION] Interrupted while waiting for booking tasks.");
                return;
            } catch (ExecutionException e) {
                println("[APPLICATION] Booking task failed unexpectedly: " + e.getCause());
            }
        }
    }
}
