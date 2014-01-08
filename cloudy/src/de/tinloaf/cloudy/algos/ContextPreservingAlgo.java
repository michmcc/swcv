package de.tinloaf.cloudy.algos;

import de.tinloaf.cloudy.algos.overlaps.ForceDirectedOverlapRemoval;
import de.tinloaf.cloudy.algos.overlaps.ForceDirectedUniformity;
import de.tinloaf.cloudy.text.Word;
import de.tinloaf.cloudy.utils.BoundingBoxGenerator;
import de.tinloaf.cloudy.utils.GeometryUtils;
import de.tinloaf.cloudy.utils.SWCPoint;
import de.tinloaf.cloudy.utils.SWCRectangle;
import de.tinloaf.cloudy.utils.WordPair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * May 12, 2013
 */
public class ContextPreservingAlgo implements LayoutAlgo
{
    private static final double EPS = 1e-6;
    private static final double KA = 15;
    private static final double KR = 1000;
    private static final double TOTAL_ITERATIONS = 1000;

    private double T = 1;

    private List<Word> lWords;
    private Map<WordPair, Double> similarity;

    private Word[] words;
    private Map<Word, SWCRectangle> wordPositions = new HashMap<Word, SWCRectangle>();

    private BoundingBoxGenerator bbGenerator;

    @Override
    public void setConstraints(BoundingBoxGenerator bbGenerator)
    {
        this.bbGenerator = bbGenerator;
    }

    @Override
    public SWCRectangle getWordRectangle(Word w)
    {
        return wordPositions.get(w);
    }

    @Override
    public void setData(List<Word> words, Map<WordPair, Double> similarity)
    {
        this.lWords = words;
        this.words = new Word[words.size()];
        int k = 0;
        for (Word w : words)
        {
            this.words[k++] = w;
        }

        this.similarity = similarity;
    }

    @Override
    public void run()
    {
        wordPositions = initialPlacement();

        //compute Delaunay
        delaunayEdges = computeDelaunay();

        runForceDirected();
    }

    private Map<Word, SWCRectangle> initialPlacement()
    {
        //find initial placement by mds layout
        MDSAlgo algo = new MDSAlgo();
        algo.setConstraints(bbGenerator);
        algo.setData(lWords, similarity);
        algo.run();

        //run mds
        Map<Word, SWCRectangle> wordPositions = new HashMap<Word, SWCRectangle>();
        for (Word w : lWords)
        {
            SWCRectangle rect = algo.getWordRectangle(w);
            wordPositions.put(w, rect);
        }

        return wordPositions;
    }

    int[][] delaunayEdges;

    /**
     * TODO
     * now the format is terrible
     */
    private int[][] computeDelaunay()
    {
        SWCPoint[] points2D = new SWCPoint[words.length];
        List<List<Integer>> edges = new ArrayList<List<Integer>>();
        GeometryUtils.computeDelaunayTriangulation(lWords, wordPositions, points2D, edges);

        SWCPoint[] points = new SWCPoint[words.length];
        for (int i = 0; i < words.length; i++)
            points[i] = new SWCPoint(points2D[i].x(), points2D[i].y());

        int res[][] = new int[words.length][];
        for (int i = 0; i < words.length; i++)
        {
            List<Integer> arr = new ArrayList<Integer>();

            List<Integer> neighborsI = edges.get(i);
            for (int j = 0; j < neighborsI.size(); j++)
            {
                List<Integer> neighborsJ = edges.get(neighborsI.get(j));

                List<Integer> commonNeighbors = new ArrayList<Integer>(neighborsI);
                commonNeighbors.retainAll(neighborsJ);

                SWCPoint pi = points2D[i];
                SWCPoint pj = points2D[neighborsI.get(j)];
                for (int k = 0; k < commonNeighbors.size(); k++)
                {
                    SWCPoint pk = points2D[commonNeighbors.get(k)];

                    if (orientation(pi, pj, pk) >= 0)
                    {
                        arr.add(neighborsI.get(j));
                        arr.add(commonNeighbors.get(k));
                    }
                    else
                    {
                        arr.add(commonNeighbors.get(k));
                        arr.add(neighborsI.get(j));
                    }
                }
            }

            res[i] = new int[arr.size()];
            for (int j = 0; j < arr.size(); j++)
                res[i][j] = arr.get(j);
        }

        return res;
    }

    private int orientation(SWCPoint pi, SWCPoint pj, SWCPoint pk)
    {
        return GeometryUtils.Cross(pi, pj, pk);
    }

    private void runForceDirected()
    {
        int numIterations = 0;

        while (numIterations++ < TOTAL_ITERATIONS)
        {
            if (numIterations % 1000 == 0)
                System.out.println("Finished Iteration " + numIterations);

            if (!doIteration())
                break;

            //cooling down the temperature (max allowed step is decreased)
            if (numIterations % 5 == 0)
                T *= 0.95;
        }

        new ForceDirectedOverlapRemoval<SWCRectangle>().run(lWords, wordPositions);
        new ForceDirectedUniformity<SWCRectangle>().run(lWords, wordPositions);
    }

    /**
     * perform several iterations
     * returns 'true' iff the last iteration moves rectangles 'alot'
     */
    public boolean doIteration(int iters)
    {
        int i = 0;
        while (i++ < iters)
        {
            if (!doIteration())
                return false;

            if (i % 5 == 0)
                T *= 0.95;
        }

        return true;
    }

    /**
     * perform one iteration
     * returns 'true' iff the iteration moves rectangles 'alot'
     */
    private boolean doIteration()
    {
        double maxWeight = computeMaxWeight();
        SWCRectangle bb = computeBoundingBox();

        double avgStep = 0;
        // compute the displacement for the word in this time step
        for (int i = 0; i < words.length; i++)
        {
            SWCRectangle rect = wordPositions.get(words[i]);
            SWCPoint dxy = new SWCPoint(0, 0);

            if (!overlap(i))
            {
                //attractive force (compact principle)
                dxy.add(computeAttractiveForce(bb, i, rect, maxWeight));
                //repulsion force (planar principle)
                dxy.add(computePlanarForce(bb, i, rect));
            }
            else
            {
                //repulsion force (removing overlaps)
                dxy.add(computeRepulsiveForce(bb, i, rect));
                //repulsion force (planar principle)
                dxy.add(computePlanarForce(bb, i, rect));
            }

            // move the rectangle
            dxy = normalize(dxy, bb);
            rect.setRect(rect.getX() + dxy.x(), rect.getY() + dxy.y(), rect.getWidth(), rect.getHeight());

            assert (!Double.isNaN(rect.getX()));
            assert (!Double.isNaN(rect.getY()));
            avgStep += dxy.distance(0, 0);
        }

        avgStep /= words.length;
        return avgStep > Math.max(bb.getWidth(), bb.getHeight()) / 10000.0;
    }

    private SWCPoint computeAttractiveForce(SWCRectangle bb, int i, SWCRectangle rectI, double maxWeight)
    {
        SWCPoint dxy = new SWCPoint(0, 0);

        double cnt = 0;
        for (int j = 0; j < words.length; j++)
        {
            if (i == j)
                continue;

            SWCRectangle rectJ = wordPositions.get(words[j]);

            double wi = Math.max(words[i].weight, maxWeight / 5.0);
            double wj = Math.max(words[j].weight, maxWeight / 5.0);
            double dist = GeometryUtils.rectToRectDistance(rectI, rectJ);
            double force = wi * wj * dist / (maxWeight * maxWeight);
            if (T < 0.5)
                force *= T;

            SWCPoint dir = new SWCPoint(rectJ.getCenterX() - rectI.getCenterX(), rectJ.getCenterY() - rectI.getCenterY());
            double len = dir.distance(0, 0);
            if (len < EPS)
                continue;

            dir.normalize();
            dir.scale(force);

            dxy.add(dir);
            cnt++;
        }

        dxy.scale(1.0 / cnt);
        return dxy;
    }

    private SWCPoint computeRepulsiveForce(SWCRectangle bb, int i, SWCRectangle rectI)
    {
        SWCPoint dxy = new SWCPoint(0, 0);

        double cnt = 0;
        for (int j = 0; j < words.length; j++)
        {
            if (i == j)
                continue;

            // compute the displacement due to the overlap repulsive force
            SWCRectangle rectJ = wordPositions.get(words[j]);

            if (rectI.intersects(rectJ))
            {
                double hix = Math.min(rectI.getMaxX(), rectJ.getMaxX());
                double lox = Math.max(rectI.getMinX(), rectJ.getMinX());
                double hiy = Math.min(rectI.getMaxY(), rectJ.getMaxY());
                double loy = Math.max(rectI.getMinY(), rectJ.getMinY());
                double dx = hix - lox; // hi > lo
                double dy = hiy - loy;
                assert (dx >= -EPS);
                assert (dy >= -EPS);

                double force = KR * Math.min(dx, dy);

                SWCPoint dir = new SWCPoint(rectJ.getCenterX() - rectI.getCenterX(), rectJ.getCenterY() - rectI.getCenterY());
                double len = dir.distance(0, 0);
                if (len < EPS)
                    continue;

                dir.normalize();
                dir.scale(force);

                dxy.subtract(dir);
                cnt++;
            }
        }

        dxy.scale(1.0 / cnt);
        return dxy;
    }

    private SWCPoint computePlanarForce(SWCRectangle bb, int i, SWCRectangle rectI)
    {
        SWCPoint dxy = new SWCPoint(0, 0);

        double cnt = 0;
        for (int t = 0; t < delaunayEdges[i].length; t += 2)
        {
            int j = delaunayEdges[i][t];
            int k = delaunayEdges[i][t + 1];

            SWCPoint pi = getCenter(i);
            SWCPoint pj = getCenter(j);
            SWCPoint pk = getCenter(k);

            if (orientation(pi, pj, pk) >= 0)
            {
                //do nothing
            }
            else
            {
                SWCPoint force = planarForce(pi, pj, pk);
                force.scale(KA);

                dxy.add(force);
                cnt++;
            }
        }

        if (cnt > 0)
            dxy.scale(1.0 / cnt);
        return dxy;
    }

    private SWCPoint planarForce(SWCPoint pi, SWCPoint pj, SWCPoint pk)
    {
        double dx = pk.x() - pj.x();
        double dy = pk.y() - pj.y();

        double dist = GeometryUtils.pointToLineDistance(new SWCPoint(pj), new SWCPoint(pk), new SWCPoint(pi));

        SWCPoint norm = new SWCPoint(-dy, dx);
        norm.scale(dist);

        return norm;
    }

    private SWCPoint getCenter(int index)
    {
        SWCPoint p = new SWCPoint(wordPositions.get(words[index]).getCenterX(), wordPositions.get(words[index]).getCenterY());
        return p;
    }

    private boolean overlap(int i)
    {
        SWCRectangle rectI = wordPositions.get(words[i]);
        for (int j = 0; j < words.length; j++)
        {
            if (i == j)
                continue;

            SWCRectangle rectJ = wordPositions.get(words[j]);
            if (rectI.intersects(rectJ))
            {
                double hix = Math.min(rectI.getMaxX(), rectJ.getMaxX());
                double lox = Math.max(rectI.getMinX(), rectJ.getMinX());
                double hiy = Math.min(rectI.getMaxY(), rectJ.getMaxY());
                double loy = Math.max(rectI.getMinY(), rectJ.getMinY());
                double dx = hix - lox; // hi > lo
                double dy = hiy - loy;
                if (Math.min(dx, dy) > 1)
                    return true;
            }
        }

        return false;
    }

    private SWCPoint normalize(SWCPoint force, SWCRectangle bb)
    {
        //maximum allowed movement is 2% of the screen width/height
        double mx = Math.min(bb.getWidth(), bb.getHeight());
        double len = force.length();
        if (len < 1e-3)
            return force;

        double maxLen = Math.min(len, T * mx / 50.0);
        force.scale(maxLen / len);
        return force;
    }

    private SWCRectangle computeBoundingBox()
    {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (SWCRectangle rect : wordPositions.values())
        {
            minX = Math.min(minX, rect.getMinX());
            maxX = Math.max(maxX, rect.getMaxX());
            minY = Math.min(minY, rect.getMinY());
            maxY = Math.max(maxY, rect.getMaxY());
        }

        return new SWCRectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private double computeMaxWeight()
    {
        double maxWeight = 0;
        for (int i = 0; i < words.length; i++)
        {
            maxWeight = Math.max(maxWeight, words[i].weight);
            assert (words[i].weight > 0.001);
        }

        return maxWeight;
    }

    public List<SWCRectangle> getDelaunay()
    {
        List<SWCRectangle> res = new ArrayList<SWCRectangle>();
        for (int i = 0; i < words.length; i++)
        {
            for (int t = 0; t < delaunayEdges[i].length; t++)
            {
                res.add(wordPositions.get(words[i]));
                res.add(wordPositions.get(words[delaunayEdges[i][t]]));
            }
        }

        return res;
    }

}