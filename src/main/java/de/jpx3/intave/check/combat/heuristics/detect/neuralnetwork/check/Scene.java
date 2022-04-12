package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.check;

import de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.NeuralNetwork;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class Scene extends JPanel {
  private final static int radius = 2;
  private final static int secondRadius = 3;
  List<Point> redPoints;
  List<Point> greenPoints;
  int sleepTime = 50;
  
  NeuralNetwork neuralNetwork;
  
  public Scene(NeuralNetwork neuralNetwork, CopyOnWriteArrayList<Point> redPoints, CopyOnWriteArrayList<Point> greenPoints) {
    this.neuralNetwork = neuralNetwork;
    
    this.redPoints = redPoints;
    this.greenPoints = greenPoints;
  }
  
  void startTrainingLoop() {
    new Thread(() -> {
      while (true) {
        if (sleepTime != 0) {
          try {
            Thread.sleep(sleepTime);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        train();
      }
    }).start();
  }
  
  void train() {
    for (Point point : redPoints) {
      double[] inputs = new double[] { point.x, point.y };
      double[] targets = new double[] { 1 };
      neuralNetwork.train(
        inputs, 
        targets
      );
    }
    for (Point point : greenPoints) {
      double[] inputs = new double[] { point.x, point.y };
      double[] targets = new double[] { 0 }; 
      neuralNetwork.train(
        inputs, 
        targets
      );
    }
  }
  
  public void paint(Graphics graphics) {
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        int index = x + y * image.getHeight();
        
        double[] inputs = new double[] {
          mapData(x, 0, image.getWidth(), -1, 1),
          mapData(y, 0, image.getHeight(), -1, 1),
        };
        double result = neuralNetwork.predict(inputs).data[0][0];
        int brightness = (int) (Math.min(Math.max(result, 0), 1) * 255d);
        Color color = new Color(brightness, 255 - brightness, 0);
        
        pixels[index] = color.getRGB();
      }
    }
    graphics.drawImage(image, 0, 0, getWidth(), getHeight(), null);
    
    ((Graphics2D) graphics).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    for (Point point : greenPoints) {
      drawPoint(graphics, point, secondRadius, Color.black);
      drawPoint(graphics, point, radius, Color.green);
    }
    
    for (Point point : redPoints) {
      drawPoint(graphics, point, secondRadius, Color.black);
      drawPoint(graphics, point, radius, Color.red);
    }
  }
  
  void drawPoint(Graphics graphics, Point point, int radius, Color color) {
    graphics.setColor(color);
    int x = (int) mapData(point.x, -1, 1, 0, getWidth());
    int y = (int) mapData(point.y, -1, 1, 0, getHeight());
    graphics.fillOval(x - radius, y - radius, radius * 2, radius * 2);
  }
  
  public static double mapData(double value, double min, double max, double minTo, double maxTo) {
    return (1 - ((value - min) / (max - min))) * minTo + ((value - min) / (max - min)) * maxTo;
  }
}