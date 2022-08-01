package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.check;

import de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.NeuralNetwork;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions.ActivationFunction.sigmoid;

public class Window extends JFrame {
  // for selftests without building the whole projekt
  public static void main(String[] args) {
    CopyOnWriteArrayList<Point> redPoints = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<Point> greenPoints = new CopyOnWriteArrayList<>();

    new Window(redPoints, greenPoints);
  }

  private static final int COMPONENT_HEIGHT = 60;
  NeuralNetwork neuralNetwork;
  Scene scene;
  JSlider sliderLearningRate;
  JSlider sliderSleepTime;
  JButton buttonSave = new JButton("save");
  JButton buttonLoad = new JButton("load");
  // for resize porpuses
  JPanel panel;

  public Window(CopyOnWriteArrayList<Point> redPoints, CopyOnWriteArrayList<Point> greenPoints) {
    neuralNetwork = new NeuralNetwork(
      2,
      sigmoid,
      20,
      sigmoid,
      1
    );

    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    panel = new JPanel(null, true);
    add(panel);

    scene = new Scene(neuralNetwork, redPoints, greenPoints);
    panel.add(scene);

    sliderLearningRate = new JSlider(0, 5000, (int) (neuralNetwork.localLearningRate * 15000));
    panel.add(sliderLearningRate);
    sliderLearningRate.addChangeListener(e -> {
      neuralNetwork.localLearningRate = (double) sliderLearningRate.getValue() / sliderLearningRate.getMaximum() / 3d;
      setTitle("learningRate " + neuralNetwork.localLearningRate + " sleepTime " + scene.sleepTime);
    });

    sliderSleepTime = new JSlider(0, 1400, scene.sleepTime);
    panel.add(sliderSleepTime);
    sliderSleepTime.addChangeListener(e -> {
      scene.sleepTime = sliderSleepTime.getValue();
      setTitle("learningRate " + neuralNetwork.localLearningRate + " sleepTime " + scene.sleepTime);
    });

    buttonSave.addActionListener(e -> {
      JFileChooser fileChooser = new JFileChooser();
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(file); ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
          Object[] objects = {scene.redPoints, scene.greenPoints};

          objectOutputStream.writeObject(objects);
        } catch (IOException exception) {
          exception.printStackTrace();
        }
      }
    });
    panel.add(buttonSave);

    buttonLoad.addActionListener(e -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setApproveButtonText("Load");
      if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();

        try (FileInputStream fileInputStream = new FileInputStream(file); ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
          Object[] objects = (Object[]) objectInputStream.readObject();
          scene.redPoints = (CopyOnWriteArrayList<Point>) objects[0];
          scene.greenPoints = (CopyOnWriteArrayList<Point>) objects[1];
        } catch (Exception exception) {
          exception.printStackTrace();
        }
      }
    });
    panel.add(buttonLoad);

    panel.addComponentListener(resizeEvent());

    setSize(800, 800 + COMPONENT_HEIGHT * 3);
    setVisible(true);
    setLocationRelativeTo(null);
    setTitle("learningRate " + neuralNetwork.localLearningRate + " sleepTime " + scene.sleepTime);

    scene.startTrainingLoop();
    startDrawLoop();
  }

  ComponentAdapter resizeEvent() {
    return new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        int width = e.getComponent().getWidth();
        int height = e.getComponent().getHeight();

        scene.setSize(width, height - COMPONENT_HEIGHT * 3);

        sliderLearningRate.setLocation(10, scene.getY() + scene.getHeight());
        sliderLearningRate.setSize(width - 20, COMPONENT_HEIGHT);

        sliderSleepTime.setLocation(10, sliderLearningRate.getY() + sliderLearningRate.getHeight());
        sliderSleepTime.setSize(width - 20, COMPONENT_HEIGHT);

        buttonSave.setLocation(10, sliderSleepTime.getY() + sliderSleepTime.getHeight() + 10);
        buttonSave.setSize(COMPONENT_HEIGHT * 2, COMPONENT_HEIGHT - 20);

        buttonLoad.setLocation(buttonSave.getX() + buttonSave.getWidth() + 10, buttonSave.getY());
        buttonLoad.setSize(buttonSave.getWidth(), buttonSave.getHeight());
      }
    };
  }

  void startDrawLoop() {
    while (true) {
      try {
        Thread.sleep(32);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      scene.repaint();
    }
  }
}