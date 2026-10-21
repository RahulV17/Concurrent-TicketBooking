# Concurrent Event Seat Booking System

Console application that manages seats for one event and takes booking requests from
multiple customers, some of them at the same time.

The point of the project is the concurrency: several requests race for the same seat
and exactly one of them wins, the rest get a clear rejection. The console is
interactive, so you can fire a batch of conflicting requests and watch it happen.

---

## 1. Tech Stack

- **Java 17**, no build tool, no dependencies
- **Console application** built on `java.util.Scanner`
- **Storage:** `ArrayList<Integer>` for available seats, `ArrayList<Booking>` for bookings
- **Concurrency:** `Thread`/`Runnable`, `volatile`, `synchronized`, `ExecutorService`

---

## 2. Run It

```bash
javac -d bin src/*.java
java -cp bin SeatBookingApplication
```

That is the whole setup. `bin/` is used instead of `out/` so it does not clash with
IntelliJ's own output folder.

---

## 3. Project Structure

```
Concurrent_Event_Seat_Booking_System/
├── src/
│   ├── Event.java                    event title + total seat count
│   ├── Booking.java                  one successful reservation
│   ├── InvalidBookingException.java  checked custom exception
│   ├── BookingService.java           owns the collections + all booking rules
│   ├── BookingTask.java              Runnable: one customer request
│   ├── StatusMonitor.java            Runnable: background status line
│   └── SeatBookingApplication.java   main(): Scanner console + wiring
├── docs/
│   ├── interview-questions.md        viva prep with pointers into the code
│   ├── sample-run-guide-scenario.txt
│   ├── sample-run-prd-scenario.txt
│   └── sample-run-edge-cases.txt
├── .gitignore
└── README.md
```

---

## 4. Class Diagram

```
┌──────────────────────┐       ┌────────────────────────┐
│        Event         │       │      Booking           │
├──────────────────────┤       ├────────────────────────┤
│ - eventName : String │       │ - customerName : String│
│ - totalSeats : int   │       │ - seatNumber   : int   │
├──────────────────────┤       ├────────────────────────┤
│ + Event(name, seats) │       │ + Booking(name, seat)  │
│ + getEventName()     │       │ + getCustomerName()    │
│ + getTotalSeats()    │       │ + getSeatNumber()      │
└──────────┬───────────┘       │ + toString()           │
           │ 1                   └───────────┬────────────┘
           │                                 │ 0..*
           ▼                                 ▼
┌──────────────────────────────────────────────────────────┐
│                      BookingService                      │
├──────────────────────────────────────────────────────────┤
│ - event          : Event                                 │
│ - availableSeats : List<Integer>  = new ArrayList<>()    │
│ - bookings       : List<Booking>  = new ArrayList<>()    │
├──────────────────────────────────────────────────────────┤
│ + BookingService(Event)                                  │
│ + synchronized Booking bookSeat(name, seat)              │
│ + synchronized List<Integer> getAvailableSeats()         │
│ + synchronized List<Booking> getBookings()               │
│ - validateSeat(int seatNumber)                           │
│ + synchronized void displaySummary()                     │
└──────────┬───────────────────────────────┬───────────────┘
           │ uses                           │ throws
           ▼                                ▼
┌────────────────────────┐      ┌──────────────────────────┐
│      BookingTask       │      │ InvalidBookingException  │
│      implements Runnable│      ├──────────────────────────┤
├────────────────────────┤      │ extends Exception        │
│ - bookingService       │      ├──────────────────────────┤
│ - customerName         │      │ + (String message)       │
│ - seatNumber           │      └──────────────────────────┘
├────────────────────────┤
│ + run()
└────────────────────────┘
           ▲ submitted to
           │
   ExecutorService (newFixedThreadPool(3))
   → pool-1-thread-1, pool-1-thread-2, pool-1-thread-3

┌────────────────────────┐
│     StatusMonitor      │
│     implements Runnable│
├────────────────────────┤
│ - volatile running     │
│ - intervalMillis       │
│ - outputLock           │  ← shared with the console
│ - firstStatus : CountDownLatch │
├────────────────────────┤
│ + StatusMonitor(long)                    │
│ + StatusMonitor(long, Object, CountDownLatch) │
│ + run()
│ + stopMonitoring()
│ - printStatus(String, boolean)
└────────────────────────┘
           ▲ started with
           │ new Thread(monitor, "status-monitor").start()

┌────────────────────────┐
│ SeatBookingApplication │
├────────────────────────┤
│ + main(String[])       │  ← Scanner console + wiring
│ - CONSOLE_LOCK         │  ← guards every prompt and every line of output
└────────────────────────┘
```

---

## 5. Core Methods

| # | Method | Class |
|---|---|---|
| 1 | `bookSeat(String, int)` | `BookingService` |
| 2 | `getAvailableSeats()` | `BookingService` |
| 3 | `getBookings()` | `BookingService` |
| 4 | `validateSeat(int)` | `BookingService` |
| 5 | `run()` | `BookingTask` |
| 6 | `run()` | `StatusMonitor` |
| 7 | `stopMonitoring()` | `StatusMonitor` |
| 8 | `displaySummary()` | `BookingService` |

`displaySummary()` was picked as the eighth because the alternative — a
`BookingBatchTask` that runs a group of bookings inside one executor task — was not
needed. Submitting several `BookingTask` objects to the pool directly does the same
job with less code.

The other methods in `SeatBookingApplication` are console plumbing (prompting,
reading, printing). None of them validate a seat or touch the collections.

---

## 6. Where the Concepts Live

| Concept | Where |
|---|---|
| **Private fields, set in constructors** | every class |
| **Clear class responsibilities** | `Event` = event data, `Booking` = one reservation, `BookingService` = the rules, `BookingTask`/`StatusMonitor` = Runnables, `SeatBookingApplication` = wiring |
| **`BookingTask implements Runnable`** | `src/BookingTask.java` |
| **`ArrayList<Integer>` for free seats** | `BookingService.availableSeats` |
| **`ArrayList<Booking>` for bookings** | `BookingService.bookings` |
| **Custom checked exception** | `InvalidBookingException extends Exception` |
| **`throw`** | `validateSeat` (range), `bookSeat` (already taken) |
| **`throws` in the signature** | `bookSeat`, `validateSeat` |
| **`try-catch`** | `BookingTask.run()` |
| **`finally`** | `BookingTask.run()` — reports the attempt ended either way |
| **Monitor thread started with `start()`** | `SeatBookingApplication.main()` |
| **`volatile`** | `StatusMonitor.running` — the stop flag |
| **`synchronized`** | the four public `BookingService` methods, all on the same object |
| **Atomic check-then-reserve** | the `contains` check, `remove` and `add` all inside `bookSeat()` |
| **`ExecutorService`** | `Executors.newFixedThreadPool(3)` in `main()` |
| **`shutdown()` + `awaitTermination()`** | `main()`, during teardown |
| **Waiting for tasks** | `Future.get()` on each submitted task, before the menu continues |
| **Interrupt handling** | `StatusMonitor.run()` restores the flag before breaking |
| **Defensive copies** | both getters return `new ArrayList<>(...)` |
| **Console thread-safety** | `CONSOLE_LOCK` — held across a whole prompt, and taken by the monitor before it prints |

---

## 7. Threading Model

| Thread | Origin | Task | Terminates when |
|---|---|---|---|
| `main` | JVM entry point | Scanner loop, submit tasks, graceful shutdown | user selects Option 5 |
| `status-monitor` | `new Thread(monitor, "status-monitor")` | `StatusMonitor.run()` — prints readiness every 5 s | `stopMonitoring()` sets `running = false` |
| `pool-1-thread-1` | `newFixedThreadPool(3)` | pulls `BookingTask`s, invokes `bookSeat()` under the lock | `executor.shutdown()` |
| `pool-1-thread-2` | `newFixedThreadPool(3)` | pulls `BookingTask`s concurrently with 1 and 3 | `executor.shutdown()` |
| `pool-1-thread-3` | `newFixedThreadPool(3)` | pulls `BookingTask`s concurrently with 1 and 2 | `executor.shutdown()` |

### Conflict resolution — Ana and Ravi both want Seat 4

1. **Concurrent dispatch** — both `BookingTask`s are submitted to the pool and picked up
   by separate workers.
2. **Lock contention** — the first worker enters `synchronized bookSeat()` and acquires
   the monitor lock on the `BookingService` instance. The second worker blocks.
3. **Atomic booking** — the first worker sees Seat 4 in `availableSeats`, removes it,
   creates `Booking[Customer: …, Seat: 4]`, adds it to `bookings`, returns, releases the lock.
4. **Re-validation** — the second worker unblocks and re-checks
   `availableSeats.contains(4)`, which is now `false`.
5. **Graceful handling** — it throws `InvalidBookingException`; `BookingTask` catches it
   and prints a `[REJECTED]` line without crashing.

### Console discipline

A background thread writing to `System.out` while the user is typing would corrupt the
prompt. `SeatBookingApplication` therefore guards the console with a single
`CONSOLE_LOCK`:

- The **main** thread holds it for an entire menu iteration — menu, prompt, action and the
  action's output are one atomic block.
- The **monitor** must acquire it before printing, and re-checks its `volatile running`
  flag once it holds the lock so it never publishes a stale line on its way out.
- The lock is **released before** `monitorThread.join()`. Holding it there would deadlock,
  because the monitor needs the lock to print its final line.

Result: the monitor reports once, at startup, at exactly the position the Guide's sample
log shows — and never interrupts a prompt. Verified by feeding input with a 6-second gap
between keystrokes (longer than the 5-second monitor interval): **no prompt was ever
split** (see `docs/sample-run-*.txt` and the test transcript below).

---

## 8. Sample Runs

Full transcripts: `docs/sample-run-guide-scenario.txt`,
`docs/sample-run-prd-scenario.txt`, `docs/sample-run-edge-cases.txt`.

> Each transcript is **one** run. Which `pool-1-thread-N` picks up which request, and in
> what order the `[SUCCESS]`/`[REJECTED]` lines appear, is intentionally
> nondeterministic (PRD §18: *"The order of success/failure messages is
> nondeterministic"*). Re-running the same input produces the same successes,
> rejections, available-seat list and summary — only the thread names and line order move.

### Guide scenario (reproduces the Guide's sample execution log)

```
Enter Event Name: Tech Meetup 2026
Enter Total Number of Seats: 10
[OK] Created Event 'Tech Meetup 2026' with 10 seats (Seats 1 to 10).
[status-monitor] [STATUS-MONITOR] Status: ACTIVE. Booking service is running and ready.

-- Option 1: Alice -> Seat 3
[pool-1-thread-1] [SUCCESS] Alice successfully booked Seat 3!

-- Option 2: Ana -> 4, Ravi -> 4, Meera -> 5
[APPLICATION] Launching 3 requests SIMULTANEOUSLY across thread pool...
[pool-1-thread-2] [SUCCESS] Ana successfully booked Seat 4!
[pool-1-thread-1] [SUCCESS] Meera successfully booked Seat 5!
[pool-1-thread-3] [REJECTED] Ravi could not book Seat 4: Seat 4 is already booked or not available.

-- Option 3
[AVAILABLE SEATS]: [1, 2, 6, 7, 8, 9, 10]

-- Option 4
[CONFIRMED BOOKINGS]:
  - Booking[Customer: Alice, Seat: 3]
  - Booking[Customer: Ana, Seat: 4]
  - Booking[Customer: Meera, Seat: 5]

-- Option 5
==================== BOOKING SUMMARY ====================
Event: Tech Meetup 2026 | Total: 10 | Booked: 3 | Remaining: 7
=========================================================
```

### Example test (Ana 3, Ravi 3, Meera 5, Dev 11)

```
[pool-1-thread-1] [SUCCESS] Ana successfully booked Seat 3!
[pool-1-thread-3] [SUCCESS] Meera successfully booked Seat 5!
[pool-1-thread-2] [REJECTED] Ravi could not book Seat 3: Seat 3 is already booked or not available.
[pool-1-thread-1] [REJECTED] Dev could not book Seat 11: Seat 11 is outside the valid range 1 to 10.
[AVAILABLE SEATS]: [1, 2, 4, 6, 7, 8, 9, 10]
[CONFIRMED BOOKINGS]:
  - Booking[Customer: Ana, Seat: 3]
  - Booking[Customer: Meera, Seat: 5]
Event: Java Community Meetup | Total: 10 | Booked: 2 | Remaining: 8
```

Ana and Ravi both want seat 3 and only one of them gets it. Dev asks for seat 11 on
a 10-seat event and is rejected. Which of Ana or Ravi wins changes from run to run —
that is the point of the test.

### Edge cases

| Input | Behaviour |
|---|---|
| Empty `Customer Name` | re-prompts: `[ERROR] This field cannot be empty.` |
| Empty `Event Name` / empty seat count | accepts the defaults `Tech Meetup 2026` / `10` |
| Seat `0`, or above capacity | `[REJECTED] … is outside the valid range 1 to N.` |
| Same seat twice in one batch | exactly one wins; the other is `[REJECTED]` |
| `Option 4` with no bookings | prints `(no bookings yet)` instead of an empty list |
| Non-numeric or out-of-`int` seat (`999999999999`) | re-prompts: `[ERROR] Please enter a whole number (maximum 2147483647).` |
| Batch count `0` | re-prompts: `[ERROR] Value must be greater than 0.` |
| Menu choice outside 1–5 | re-prompts: `[ERROR] Invalid option. …` |
| Input stream closed (EOF, e.g. piped input exhausted) | exits cleanly with code 0 — no `NoSuchElementException` stack trace |

### Concurrency stress test

A throwaway harness (not part of the project) ran 200 rounds × 60 concurrent requests
against a 10-seat event — **12,000 requests, 0 double bookings**, with
`booked + available == total` and no duplicate seat numbers in every round. The same
harness with `synchronized` removed from `bookSeat` reported duplicate bookings within
the first round.

---

## 9. Design Decisions

A few things that were judgement calls rather than obvious choices.

**The menu.** The brief for this project was deliberately narrow — class design,
collections, validation, concurrency — so the menu could have been a hardcoded demo.
It is interactive instead, because you cannot see a race condition without being able
to fire two conflicting requests yourself. The four non-zero bookings in the transcript
above are one click apart.

**The rejection message.** `Seat 4 is already booked or not available.` The shorter
`Seat 4 is already booked.` would read better, but the longer one is what the brief's
code sample used, so it is used verbatim.

**The monitor interval.** 5 seconds, per the thread table in the brief. The sample
pseudocode used 1 second. `MONITOR_INTERVAL_MILLIS` changes it.

**The defaults.** `Tech Meetup 2026` and 10 seats. Neither was specified, so they are
taken from the sample run in the brief.

**The monitor prints once, not every 5 seconds.** This is the one real deviation. The
sample log shows a single status line at startup, and a status line landing in the
middle of a prompt is unusable in an interactive console. The periodic loop is still
there and still works — it just waits for the console to be free rather than
interrupting you. Verified by leaving the app idle at the menu for 14 seconds with a
5-second interval: one line, not three.

**`CONSOLE_LOCK` and `firstStatus` are additions.** Neither is in the brief. The first
stops the monitor from splitting a line you are typing. The second guarantees the
readiness line appears before the menu rather than in the middle of it.
