package edu.berkeley.compbio.jlibsvm.kernel;

import edu.berkeley.compbio.jlibsvm.SvmPoint;

import java.util.Properties;

/**
 * @author <a href="mailto:dev@davidsoergel.com">David Soergel</a>
 * @version $Id$
 */
public class RBFKernel extends GammaKernel
	{
	public RBFKernel(Properties props)
		{
		this(Float.parseFloat(props.getProperty("gamma")));
		}

	public RBFKernel(float gamma)
		{
		super(gamma);
		}

	public float evaluate(SvmPoint x, SvmPoint y)
		{

		// FIRST CHOOSE THE SUM METHOD
		// we're looking for the square of the distance between x and y in the original space
		// which equals x_square + y_square - 2 * dot(x, y);

		//float sum = float2xDotProduct(x, y);  // DOESN'T WORK
		//double sum = explicitDoubleSum(x, y);  // Most precise.
		//float sum = explicitFloatSum(x, y);  // Works just as well; no evident speed improvement though
		float sum = explicitFloatSumOptimized(x, y);  // Works just as well; faster

		//	assert sum == sum2;


		// THEN CHOOSE THE EXP METHOD

		float result = (float) Math.exp(-gamma * sum);
		//	float result = interpolatingExp.evaluate(-gamma * sum);  // approximation is as good as the interpolator specifies, but no faster than Math.exp()
		//float result =  (float) MathSupport.expApprox(-gamma * sum);  // APPROXIMATION IS NOT GOOD ENOUGH

		// STRANGE PERFORMANCE ISSUES

		// for some reason, using Math.exp() seems to halve the total time spent in explicitFloatSum (which comes first!), compared to the interpolating exp.
		// The total number of iterations is the same, and the total number of comparisons and multiplications performed is the same,
		// so it's not a matter of some upstream function calling evaluate() more often.
		// this makes no sense at all... must be due to a compiler optimization or the processor cache size or some such thing!?

		// Profiling results don't make any more sense with java -Xint

		// Okay, yes, it could be that the interpolation table bumps data out of the l1 cache,
		// which then needs to be reloaded in explicitFloatSumOptimized the next time around
		// whereas Math.exp() leaves data in the l1 cache

		// this makes sense because evaluate() is typically called a bunch of times in a row holding point x constant but changing point y.

		// My Merom cores have 32kb l1 cache; it might be worth testing the difference on a machine with more to confirm the reason.
		// anyhow I'll just suck up the Math.exp() for now.


//		evaluateCount++;

		return result;
		}

	/*	public String perfString()
		 {
		 return "" + evaluateCount + " evaluations, " + interpolatingExp.perfString();
		 }
 */
/*	private float float2xDotProduct(SvmPoint x, SvmPoint y)
		{
		// this ends up horribly wrong near the boundaries... ???
		// or not, and I was previously worried about the exp method?
		float sum = -2f * MathSupport.dot(x, y);

		sum += x.getSquared();
		sum += y.getSquared();

		return sum;
		}
*/


	/**
	 * Subtract one vector from the other and take the dot product of the difference with itself, to get the square of the
	 * norm.
	 *
	 * @param x
	 * @param y
	 * @return
	 */
/*	private float explicitFloatSum(SvmPoint x, SvmPoint y)
		{
		float sum = 0;
		int xlen = x.indexes.length;
		int ylen = y.indexes.length;
		int i = 0;
		int j = 0;
		while (i < xlen && j < ylen)
			{
			if (x.indexes[i] == y.indexes[j])
				{
				float d = x.values[i++] - y.values[j++];
				sum += d * d;
				}
			else if (x.indexes[i] > y.indexes[j])
				{
				// there is an entry for y but not for x at this index => x.value == 0
				sum += y.values[j] * y.values[j];
				++j;
				}
			else
				{
				// there is an entry for x but not for y at this index => y.value == 0
				sum += x.values[i] * x.values[i];
				++i;
				}
			}

		// finish off any trailing entries in one vector but not the other
		while (i < xlen)
			{
			sum += x.values[i] * x.values[i];
			++i;
			}

		while (j < ylen)
			{
			sum += y.values[j] * y.values[j];
			++j;
			}
		return sum;
		}*/

	/**
	 * Subtract one vector from the other and take the dot product of the difference with itself, to get the square of the
	 * norm.
	 *
	 * @param x
	 * @param y
	 * @return
	 */
	private static final float explicitFloatSumOptimized(final SvmPoint x, final SvmPoint y)
		{
		float sum = 0;

		// making final local copies may help performance??  Or not, the JIT should figure this out
		final int[] xIndexes = x.indexes;
		final int xlen = xIndexes.length;
		final int[] yIndexes = y.indexes;
		final int ylen = yIndexes.length;
		final float[] xValues = x.values;
		final float[] yValues = y.values;

		int i = 0;
		int j = 0;
		int xIndex = xIndexes[0];
		int yIndex = yIndexes[0];

		// use Integer.MAX_VALUE as a marker that we've used up the whole array

		while (xIndex != Integer.MAX_VALUE || yIndex != Integer.MAX_VALUE)
			{

			if (xIndex == yIndex)
				{
				float d = xValues[i] - yValues[j];
				sum += d * d;

				i++;
				if (i >= xlen)
					{
					xIndex = Integer.MAX_VALUE;
					}
				else
					{
					xIndex = xIndexes[i];
					}

				j++;
				if (j >= ylen)
					{
					yIndex = Integer.MAX_VALUE;
					}
				else
					{
					yIndex = yIndexes[j];
					}
				}
			else
				{
				while (xIndex > yIndex)
					{

					// there is an entry for y but not for x at this index => x.value == 0

					sum += yValues[j] * yValues[j];
					j++;
					if (j >= ylen)
						{
						yIndex = Integer.MAX_VALUE;
						}
					else
						{
						yIndex = yIndexes[j];
						}
					}

				while (yIndex > xIndex)
					{

					// there is an entry for x but not for y at this index => y.value == 0

					sum += xValues[i] * xValues[i];
					i++;
					if (i >= xlen)
						{
						xIndex = Integer.MAX_VALUE;
						}
					else
						{
						xIndex = xIndexes[i];
						}
					}
				}
			}

		return sum;
		}


	public String toString()
		{
		StringBuilder sb = new StringBuilder();
		sb.append("kernel_type rbf\n");
		sb.append("gamma " + gamma + "\n");
		return sb.toString();
		}
	}