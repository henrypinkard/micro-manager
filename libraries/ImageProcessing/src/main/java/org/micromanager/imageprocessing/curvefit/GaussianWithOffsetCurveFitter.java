package org.micromanager.imageprocessing.curvefit;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.exception.ZeroException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.fitting.AbstractCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.util.FastMath;


/**
 * Used to fit a curve with a Gaussian.
 *
 * @author nico
 */
public class GaussianWithOffsetCurveFitter extends AbstractCurveFitter {

   /** Parametric function to be fitted. */
   private static final Gaussian.Parametric FUNCTION = new Gaussian.Parametric() {
      @Override
      public double value(double x, double ... p) {
         double v = Double.POSITIVE_INFINITY;
         try {
            double[] p2 = new double[3];
            System.arraycopy(p, 0, p2, 0, 3);
            v = super.value(x, p2) + p[3];
         } catch (NotStrictlyPositiveException e) { // NOPMD
            // Do nothing.
         }
         return v;
      }

      @Override
      public double[] gradient(double x, double ... p) {
         double[] v = { Double.POSITIVE_INFINITY,
               Double.POSITIVE_INFINITY,
               Double.POSITIVE_INFINITY };
         try {
            double[] p2 = new double[3];
            System.arraycopy(p, 0, p2, 0, 3);
            v = super.gradient(x, p2);
         } catch (NotStrictlyPositiveException e) { // NOPMD
            // Do nothing.
         }
         double[] w = new double[4];
         System.arraycopy(v, 0, w, 0, 3);
         w[3] = 0;
         return w;
      }
   };
   /** Initial guess. */
   private final double[] initialGuess;
   /** Maximum number of iterations of the optimization algorithm. */
   private final int maxIter;

   /**
    * Contructor used by the factory methods.
    *
    * @param initialGuess Initial guess. If set to {@code null}, the initial guess
    *     will be estimated using the {@link ParameterGuesser}.
    * @param maxIter Maximum number of iterations of the optimization algorithm.
    */
   private GaussianWithOffsetCurveFitter(double[] initialGuess,
                                         int maxIter) {
      this.initialGuess = initialGuess;
      this.maxIter = maxIter;
   }

   /**
    * Creates a default curve fitter.
    * The initial guess for the parameters will be {@link ParameterGuesser}
    * computed automatically, and the maximum number of iterations of the
    * optimization algorithm is set to {@link Integer#MAX_VALUE}.
    *
    * @return a curve fitter.
    *
    * @see #withStartPoint(double[])
    * @see #withMaxIterations(int)
    */
   public static GaussianWithOffsetCurveFitter create() {
      return new GaussianWithOffsetCurveFitter(null, Integer.MAX_VALUE);
   }

   /**
    * Configure the start point (initial guess).
    *
    * @param newStart new start point (initial guess)
    * @return a new instance.
    */
   public GaussianWithOffsetCurveFitter withStartPoint(double[] newStart) {
      return new GaussianWithOffsetCurveFitter(newStart.clone(),
              maxIter);
   }

   /**
    * Configure the maximum number of iterations.
    *
    * @param newMaxIter maximum number of iterations
    * @return a new instance.
    */
   public GaussianWithOffsetCurveFitter withMaxIterations(int newMaxIter) {
      return new GaussianWithOffsetCurveFitter(initialGuess,
              newMaxIter);
   }

   /**
    * Needed for the fitter.
    *
    * @param observations Data for which we seek the LeastSquaresProblem
    *
    * @return The problem
    */
   @Override
   protected LeastSquaresProblem getProblem(Collection<WeightedObservedPoint> observations) {

      // Prepare least-squares problem.
      final int len = observations.size();
      final double[] target  = new double[len];
      final double[] weights = new double[len];

      int i = 0;
      for (WeightedObservedPoint obs : observations) {
         target[i]  = obs.getY();
         weights[i] = obs.getWeight();
         ++i;
      }

      final AbstractCurveFitter.TheoreticalValuesFunction model =
              new AbstractCurveFitter.TheoreticalValuesFunction(FUNCTION, observations);

      final double[] startPoint = initialGuess != null ? initialGuess :
              // Compute estimation.
              new ParameterGuesser(observations).guess();

      // Return a new least squares problem set up to fit a Gaussian curve to the
      // observed points.
      return new LeastSquaresBuilder()
              .maxEvaluations(Integer.MAX_VALUE)
              .maxIterations(maxIter)
              .start(startPoint)
              .target(target)
              .weight(new DiagonalMatrix(weights))
              .model(model.getModelFunction(),
                      model.getModelFunctionJacobian()).build();

   }

   /**
    * Guesses the parameters {@code norm}, {@code mean}, and {@code sigma}
    * of a {@link org.apache.commons.math3.analysis.function.Gaussian.Parametric}
    * based on the specified observed points.
    */
   public static class ParameterGuesser {
      /** Normalization factor. */
      private final double norm;
      /** Mean. */
      private final double mean;
      /** Standard deviation. */
      private final double sigma;
      /** offset. **/
      private final double offset;

      /**
       * Constructs instance with the specified observed points.
       *
       * @param observations Observed points from which to guess the
       *     parameters of the Gaussian.
       * @throws NullArgumentException if {@code observations} is
       *     {@code null}.
       * @throws NumberIsTooSmallException if there are less than 3
       *     observations.
       */
      public ParameterGuesser(Collection<WeightedObservedPoint> observations) {
         if (observations == null) {
            throw new NullArgumentException(LocalizedFormats.INPUT_ARRAY);
         }
         if (observations.size() < 3) {
            throw new NumberIsTooSmallException(observations.size(), 3, true);
         }

         final List<WeightedObservedPoint> sorted = sortObservations(observations);
         final double[] params = basicGuess(sorted.toArray(new WeightedObservedPoint[0]));

         norm = params[0];
         mean = params[1];
         sigma = params[2];
         offset = params[3];
      }

      /**
       * Gets an estimation of the parameters.
       *
       * @return the guessed parameters, in the following order:
       *     <ul>
       *        <li>Normalization factor</li>
       *        <li>Mean</li>
       *        <li>Standard deviation</li>
       *     </ul>
       */
      public double[] guess() {
         return new double[] { norm, mean, sigma, offset };
      }

      /**
       * Sort the observations.
       *
       * @param unsorted Input observations.
       * @return the input observations, sorted.
       */
      private List<WeightedObservedPoint> sortObservations(
              Collection<WeightedObservedPoint> unsorted) {
         final List<WeightedObservedPoint> observations = new ArrayList<>(unsorted);

         final Comparator<WeightedObservedPoint> cmp = new Comparator<WeightedObservedPoint>() {
            @Override
            public int compare(WeightedObservedPoint p1,
                               WeightedObservedPoint p2) {
               if (p1 == null && p2 == null) {
                  return 0;
               }
               if (p1 == null) {
                  return -1;
               }
               if (p2 == null) {
                  return 1;
               }
               if (p1.getX() < p2.getX()) {
                  return -1;
               }
               if (p1.getX() > p2.getX()) {
                  return 1;
               }
               if (p1.getY() < p2.getY()) {
                  return -1;
               }
               if (p1.getY() > p2.getY()) {
                  return 1;
               }
               if (p1.getWeight() < p2.getWeight()) {
                  return -1;
               }
               if (p1.getWeight() > p2.getWeight()) {
                  return 1;
               }
               return 0;
            }
         };

         Collections.sort(observations, cmp);
         return observations;
      }

      /**
       * Guesses the parameters based on the specified observed points.
       *
       * @param points Observed points, sorted.
       * @return the guessed parameters (normalization factor, mean and
       *     sigma).
       */
      private double[] basicGuess(WeightedObservedPoint[] points) {
         final int maxYIdx = findMaxY(points);
         final int minYIdx = findMinY(points);
         final double o = points[minYIdx].getY();
         final double n = points[maxYIdx].getY();
         final double m = points[maxYIdx].getX();

         double fwhmApprox;
         try {
            final double halfY = n + ((m - n) / 2);
            final double fwhmX1 = interpolateXAtY(points, maxYIdx, -1, halfY);
            final double fwhmX2 = interpolateXAtY(points, maxYIdx, 1, halfY);
            fwhmApprox = fwhmX2 - fwhmX1;
         } catch (OutOfRangeException e) {
            // TODO: Exceptions should not be used for flow control.
            fwhmApprox = points[points.length - 1].getX() - points[0].getX();
         }
         final double s = fwhmApprox / (2 * FastMath.sqrt(2 * FastMath.log(2)));

         return new double[] { n, m, s, o };
      }

      /**
       * Finds index of point in specified points with the largest Y.
       *
       * @param points Points to search.
       * @return the index in specified points array.
       */
      private int findMaxY(WeightedObservedPoint[] points) {
         int maxYIdx = 0;
         for (int i = 1; i < points.length; i++) {
            if (points[i].getY() > points[maxYIdx].getY()) {
               maxYIdx = i;
            }
         }
         return maxYIdx;
      }

      /**
       * Finds index of point in specified points with the smalles Y.
       *
       * @param points Points to search.
       * @return the index in specified points array.
       */
      private int findMinY(WeightedObservedPoint[] points) {
         int minYIdx = 0;
         for (int i = 1; i < points.length; i++) {
            if (points[i].getY() < points[minYIdx].getY()) {
               minYIdx = i;
            }
         }
         return minYIdx;
      }

      /**
       * Interpolates using the specified points to determine X at the
       * specified Y.
       *
       * @param points Points to use for interpolation.
       * @param startIdx Index within points from which to start the search for
       *     interpolation bounds points.
       * @param idxStep Index step for searching interpolation bounds points.
       * @param y Y value for which X should be determined.
       * @return the value of X for the specified Y.
       * @throws ZeroException if {@code idxStep} is 0.
       * @throws OutOfRangeException if specified {@code y} is not within the
       *     range of the specified {@code points}.
       */
      private double interpolateXAtY(WeightedObservedPoint[] points,
                                     int startIdx,
                                     int idxStep,
                                     double y)
              throws OutOfRangeException {
         if (idxStep == 0) {
            throw new ZeroException();
         }
         final WeightedObservedPoint[] twoPoints
                 = getInterpolationPointsForY(points, startIdx, idxStep, y);
         final WeightedObservedPoint p1 = twoPoints[0];
         final WeightedObservedPoint p2 = twoPoints[1];
         if (p1.getY() == y) {
            return p1.getX();
         }
         if (p2.getY() == y) {
            return p2.getX();
         }
         return p1.getX() + (((y - p1.getY()) * (p2.getX() - p1.getX()))
                 / (p2.getY() - p1.getY()));
      }

      /**
       * Gets the two bounding interpolation points from the specified points
       * suitable for determining X at the specified Y.
       *
       * @param points Points to use for interpolation.
       * @param startIdx Index within points from which to start search for
       *     interpolation bounds points.
       * @param idxStep Index step for search for interpolation bounds points.
       * @param y Y value for which X should be determined.
       * @return the array containing two points suitable for determining X at
       *     the specified Y.
       * @throws ZeroException if {@code idxStep} is 0.
       * @throws OutOfRangeException if specified {@code y} is not within the
       *     range of the specified {@code points}.
       */
      private WeightedObservedPoint[] getInterpolationPointsForY(WeightedObservedPoint[] points,
                                                                 int startIdx,
                                                                 int idxStep,
                                                                 double y)
              throws OutOfRangeException {
         if (idxStep == 0) {
            throw new ZeroException();
         }
         for (int i = startIdx;
               idxStep < 0 ? i + idxStep >= 0 : i + idxStep < points.length;
               i += idxStep) {
            final WeightedObservedPoint p1 = points[i];
            final WeightedObservedPoint p2 = points[i + idxStep];
            if (isBetween(y, p1.getY(), p2.getY())) {
               if (idxStep < 0) {
                  return new WeightedObservedPoint[] { p2, p1 };
               } else {
                  return new WeightedObservedPoint[] { p1, p2 };
               }
            }
         }

         // Boundaries are replaced by dummy values because the raised
         // exception is caught and the message never displayed.
         // TODO: Exceptions should not be used for flow control.
         throw new OutOfRangeException(y,
                 Double.NEGATIVE_INFINITY,
                 Double.POSITIVE_INFINITY);
      }

      /**
       * Determines whether a value is between two other values.
       *
       * @param value Value to test whether it is between {@code boundary1}
       *     and {@code boundary2}.
       * @param boundary1 One end of the range.
       * @param boundary2 Other end of the range.
       * @return {@code true} if {@code value} is between {@code boundary1} and
       *     {@code boundary2} (inclusive), {@code false} otherwise.
       */
      private boolean isBetween(double value,
                                double boundary1,
                                double boundary2) {
         return (value >= boundary1 && value <= boundary2)
                 || (value >= boundary2 && value <= boundary1);
      }
   }
}
