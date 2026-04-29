package de.jpx3.intave.check.combat.clickpatterns;

import java.util.Collection;

public final class ClickMathUtils {

    private ClickMathUtils() {}

    public static double getMean(Collection<? extends Number> data) {
        if (data.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Number number : data) {
            sum += number.doubleValue();
        }
        return sum / data.size();
    }

    public static double getCPS(Collection<? extends Number> data) {
        double mean = getMean(data);
        if (mean == 0.0) return 0.0;
        return 20.0 / mean;
    }

    public static double getVariance(Collection<? extends Number> data) {
        if (data.isEmpty()) return 0.0;
        int count = 0;
        double sum = 0.0;
        double variance = 0.0;
        for (Number number : data) {
            sum += number.doubleValue();
            count++;
        }
        double mean = sum / count;
        for (Number number : data) {
            variance += Math.pow(number.doubleValue() - mean, 2.0);
        }
        return variance / count;
    }

    public static double getStandardDeviation(Collection<? extends Number> data) {
        return Math.sqrt(getVariance(data));
    }

    public static double getKurtosis(Collection<? extends Number> data) {
        if (data.isEmpty()) return 0.0;
        double sum = 0.0;
        int count = 0;
        for (Number number : data) {
            sum += number.doubleValue();
            count++;
        }
        if (count < 4) {
            return 0.0;
        }
        double efficiencyFirst = count * (count + 1.0) / ((count - 1.0) * (count - 2.0) * (count - 3.0));
        double efficiencySecond = 3.0 * Math.pow(count - 1.0, 2.0) / ((count - 2.0) * (count - 3.0));
        double average = sum / count;
        double variance = 0.0;
        double varianceSquared = 0.0;
        for (Number number : data) {
            variance += Math.pow(average - number.doubleValue(), 2.0);
            varianceSquared += Math.pow(average - number.doubleValue(), 4.0);
        }
        double varianceDivCount = variance / count;
        if (varianceDivCount == 0) return 0.0;
        return efficiencyFirst * (varianceSquared / Math.pow(varianceDivCount, 2.0)) - efficiencySecond;
    }

    public static double getSkewness(Collection<? extends Number> data) {
        if (data.isEmpty()) return 0.0;
        double sum = 0.0;
        int count = 0;
        for (Number number : data) {
            sum += number.doubleValue();
            count++;
        }
        double average = sum / count;
        double variance = 0.0;
        double varianceSquared = 0.0;
        for (Number number : data) {
            variance += Math.pow(average - number.doubleValue(), 2.0);
            varianceSquared += Math.pow(average - number.doubleValue(), 3.0);
        }
        if (variance == 0) return 0.0;
        return Math.sqrt(count) * varianceSquared / Math.pow(variance, 1.5);
    }

    public static int getRange(Collection<? extends Number> data) {
        if (data.isEmpty()) return 0;
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;
        for (Number n : data) {
            double val = n.doubleValue();
            if (val > max) max = val;
            if (val < min) min = val;
        }
        return (int) (max - min);
    }

    public static double getRatioOfValue(Collection<? extends Number> data, double targetValue, double tolerance) {
        if (data.isEmpty()) return 0.0;
        long count = 0;
        for (Number n : data) {
            if (Math.abs(n.doubleValue() - targetValue) <= tolerance) {
                count++;
            }
        }
        return (double) count / data.size();
    }

    public static double calculateWindowDrift(Collection<? extends Number> data, int windowSize) {
        if (data.size() < windowSize * 2) return 100.0;
        Number[] arr = data.toArray(new Number[0]);
        int numWindows = arr.length / windowSize;
        if (numWindows < 2) return 100.0;

        double[] windowMeans = new double[numWindows];
        for (int w = 0; w < numWindows; w++) {
            double sum = 0;
            int count = 0;
            for (int i = w * windowSize; i < (w + 1) * windowSize && i < arr.length; i++) {
                sum += arr[i].doubleValue();
                count++;
            }
            windowMeans[w] = count > 0 ? sum / count : 0;
        }

        double totalDrift = 0;
        for (int i = 1; i < windowMeans.length; i++) {
            totalDrift += Math.abs(windowMeans[i] - windowMeans[i-1]);
        }
        return totalDrift / (windowMeans.length - 1);
    }

    public static double getEntropy(Collection<? extends Number> data) {
        if (data == null || data.isEmpty()) return 0.0;
        java.util.Map<Double, Integer> frequencyMap = new java.util.HashMap<>();
        for (Number number : data) {
            double value = number.doubleValue();
            frequencyMap.put(value, frequencyMap.getOrDefault(value, 0) + 1);
        }
        double entropy = 0.0;
        double total = data.size();
        for (java.util.Map.Entry<Double, Integer> entry : frequencyMap.entrySet()) {
            double probability = entry.getValue().doubleValue() / total;
            entropy -= probability * (Math.log(probability) / Math.log(2.0));
        }
        return entropy;
    }

    public static double giniCoefficient(Collection<? extends Number> data) {
        if (data == null || data.size() < 2) return 0.0;
        Number[] dataArray = data.toArray(new Number[0]);
        int n = dataArray.length;
        double[] values = new double[n];
        for (int i = 0; i < n; i++) values[i] = dataArray[i].doubleValue();
        java.util.Arrays.sort(values);
        double cumulativeSum = 0.0;
        double cumulativeValuesSum = 0.0;
        for (int i = 0; i < n; i++) {
            cumulativeValuesSum += values[i];
            cumulativeSum += cumulativeValuesSum;
        }
        if (cumulativeValuesSum == 0) return 0.0;
        return (n + 1.0 - 2.0 * cumulativeSum / cumulativeValuesSum) / n;
    }

    public static double calculateSerialCorrelation(Collection<? extends Number> data) {
        if (data == null || data.size() < 2) return 0.0;
        double mean = getMean(data);
        double numerator = 0.0;
        double denominator = 0.0;
        Number[] dataArray = data.toArray(new Number[0]);
        for (int i = 0; i < dataArray.length - 1; i++) {
            numerator += (dataArray[i].doubleValue() - mean) * (dataArray[i + 1].doubleValue() - mean);
        }
        for (Number number : dataArray) {
            denominator += Math.pow(number.doubleValue() - mean, 2.0);
        }
        if (denominator == 0) return 0.0;
        return numerator / denominator;
    }

    public static double calculateSimilarity(Collection<? extends Number> currentList, Collection<? extends Number> lastList) {
        if (currentList.size() != lastList.size() || lastList.isEmpty()) {
            return Double.MAX_VALUE;
        }
        int totalComparisons = currentList.size() * lastList.size();
        double totalDifference = 0.0;

        for (Number current : currentList) {
            for (Number last : lastList) {
                totalDifference += Math.abs(current.doubleValue() - last.doubleValue());
            }
        }

        double max = Double.MIN_VALUE;
        for (Number value : lastList) {
            if (value.doubleValue() > max) {
                max = value.doubleValue();
            }
        }

        double maxPossibleDifference = totalComparisons * max;
        if (maxPossibleDifference == 0) return 0.0;
        return 1.0 - (totalDifference / maxPossibleDifference);
    }

    public static double getMode(Collection<? extends Number> data) {
        if (data == null || data.isEmpty()) return 0.0;
        java.util.Map<Double, Integer> frequencyMap = new java.util.HashMap<>();
        for (Number number : data) {
            double value = number.doubleValue();
            frequencyMap.put(value, frequencyMap.getOrDefault(value, 0) + 1);
        }
        double mode = 0.0;
        int maxFrequency = 0;
        for (java.util.Map.Entry<Double, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() > maxFrequency) {
                maxFrequency = entry.getValue();
                mode = entry.getKey();
            }
        }
        return mode;
    }
}
