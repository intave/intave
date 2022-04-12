package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.check;

import de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.NeuralNetwork;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Window extends JFrame {
  NeuralNetwork neuralNetwork;
  
  public Window(NeuralNetwork neuralNetwork, CopyOnWriteArrayList<Point> redPoints, CopyOnWriteArrayList<Point> greenPoints) {
    this.neuralNetwork = neuralNetwork;
    
    int sliderHeight = 60;
    JFrame frame = new JFrame();
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    
    JPanel panel = new JPanel(null, true);
    frame.add(panel);
    
    Scene scene = new Scene(neuralNetwork, redPoints, greenPoints);
    panel.add(scene);
    
    JSlider sliderLearningRate = new JSlider(0, 5000, (int) (neuralNetwork.localLearningRate * 15000));
    panel.add(sliderLearningRate);
    sliderLearningRate.addChangeListener(e -> {
      neuralNetwork.localLearningRate = (double) sliderLearningRate.getValue() / sliderLearningRate.getMaximum() / 3d;
      frame.setTitle("learningRate " + neuralNetwork.localLearningRate + " sleepTime " + scene.sleepTime);
    });
    
    JSlider sliderSleepTime = new JSlider(0, 1400, scene.sleepTime);
    panel.add(sliderSleepTime);
    sliderSleepTime.addChangeListener(e -> {
      scene.sleepTime = sliderSleepTime.getValue();
      frame.setTitle("learningRate " + neuralNetwork.localLearningRate + " sleepTime " + scene.sleepTime);
    });
    
    JButton buttonSave = new JButton("save");
    buttonSave.addActionListener(e -> {
      JFileChooser fileChooser = new JFileChooser();
      if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        try (
          FileOutputStream fileOutputStream = new FileOutputStream(file);
          ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)
        ) {
          Object[] objects = {
            scene.redPoints,
            scene.greenPoints
          };
          
          objectOutputStream.writeObject(objects);
        } catch (FileNotFoundException ex) {
          ex.printStackTrace();
        } catch (IOException ioException) {
          ioException.printStackTrace();
        }
      }
    });
    panel.add(buttonSave);
    
    JButton buttonLoad = new JButton("load");
    buttonLoad.addActionListener(e -> {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setApproveButtonText("Load");
      if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
        File file = fileChooser.getSelectedFile();
        
        try (
          FileInputStream fileInputStream = new FileInputStream(file);
          ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)
        ) {
          Object[] objects = (Object[]) objectInputStream.readObject();
          scene.redPoints = (CopyOnWriteArrayList<Point>) objects[0];
          scene.greenPoints = (CopyOnWriteArrayList<Point>) objects[1];
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    });
    panel.add(buttonLoad);
    
    panel.addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        int width = e.getComponent().getWidth();
        int height = e.getComponent().getHeight();
        
        scene.setSize(width, height - sliderHeight * 3);
        
        sliderLearningRate.setLocation(10, scene.getY() + scene.getHeight());
        sliderLearningRate.setSize(width - 20, sliderHeight);
        
        sliderSleepTime.setLocation(10, sliderLearningRate.getY() + sliderLearningRate.getHeight());
        sliderSleepTime.setSize(width - 20, sliderHeight);
        
        buttonSave.setLocation(10, sliderSleepTime.getY() + sliderSleepTime.getHeight() + 10);
        buttonSave.setSize(sliderHeight * 2, sliderHeight - 20);
        
        buttonLoad.setLocation(buttonSave.getX() + buttonSave.getWidth() + 10, buttonSave.getY());
        buttonLoad.setSize(buttonSave.getWidth(), buttonSave.getHeight());
      }
    });
    
    frame.setSize(800, 800 + sliderHeight * 3);
    frame.setVisible(true);
    frame.setLocationRelativeTo(null);
    frame.setTitle("learningRate " + neuralNetwork.localLearningRate + " sleepTime " + scene.sleepTime);
    
    scene.startTrainingLoop();
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