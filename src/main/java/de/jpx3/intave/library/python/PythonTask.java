package de.jpx3.intave.library.python;

import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.executor.IntaveThreadFactory;

import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public final class PythonTask implements Runnable {
  private static final ThreadFactory FACTORY = IntaveThreadFactory.ofPriority(5);

  private final String pythonCommand;
  private final File pythonFile;

  private Process process;
  private Writer writer;
  private Reader reader;
  private CountDownLatch countDownLatch;

  public PythonTask(String pythonCommand, File pythonFile) {
    this.pythonCommand = pythonCommand;
    this.pythonFile = pythonFile;
  }

  public void start() {
    Thread thread = FACTORY.newThread(this);
    countDownLatch = new CountDownLatch(1);
    thread.start();
    try {
      countDownLatch.await();
    } catch (InterruptedException exception) {
      throw new RuntimeException(exception);
    }
    ShutdownTasks.add(this::stop);
  }

  public void stop() {
    try {
      countDownLatch.await();
      process.destroyForcibly();
    } catch (InterruptedException exception) {
      throw new RuntimeException(exception);
    }
  }

  public synchronized void feedLine(String input) {
    if (process == null) {
      throw new IllegalStateException("Process not started");
    }
    try {
      writer.write(input);
      writer.flush();
    } catch (IOException exception) {
      exception.printStackTrace();
    }
  }

  public synchronized String readLine() {
    if (process == null) {
      throw new IllegalStateException("Process not started");
    }
    try {
      StringBuilder builder = new StringBuilder();
      while (builder.length() <= 0 || builder.charAt(builder.length() - 1) != '\n') {
        builder.append((char) reader.read());
      }
      return builder.toString();
    } catch (IOException exception) {
      exception.printStackTrace();
      return null;
    }
  }

  public synchronized String feedLineAndRead(String input) {
    feedLine(input);
    return readLine();
  }

  public synchronized void feedLineAndRead(String input, Consumer<? super String> result) {
    feedLine(input);
    result.accept(readLine());
  }

  @Override
  public void run() {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(this.pythonCommand, this.pythonFile.getAbsolutePath())
//        .redirectError(INHERIT)
      ;
      process = processBuilder.start();
      reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
      countDownLatch.countDown();
      int exitCode = process.waitFor();
//      System.out.println("Exitcode " + exitCode);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
  }
}
