package de.tinloaf.cloudy.algos;

import de.tinloaf.cloudy.text.Word;
import de.tinloaf.cloudy.utils.BoundingBoxGenerator;
import de.tinloaf.cloudy.utils.Logger;
import de.tinloaf.cloudy.utils.SWCRectangle;
import de.tinloaf.cloudy.utils.WordPair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author spupyrev
 * May 15, 2013
 */
public class SinglePathAlgo implements LayoutAlgo
{
    private List<Word> cycle;
    private Map<WordPair, Double> similarity;

    private Map<Word, SWCRectangle> wordPositions;
    private SWCRectangle[] rec;

    private BoundingBoxGenerator bbGenerator;

    public SinglePathAlgo()
    {
    }

    @Override
    public void setConstraints(BoundingBoxGenerator bbGenerator)
    {
        this.bbGenerator = bbGenerator;
    }

    @Override
    public void setData(List<Word> cycle, Map<WordPair, Double> similarity)
    {
        this.cycle = cycle;
        this.similarity = similarity;
    }

    @Override
    public void run()
    {
        //remove lightest edge
        generatePath();

        //create rectangles
        generateBoundingBoxes();

        rec[0].move(0, 0);

        // -> 0
        // down 1
        // <- 2
        // up 3
        int prevDir = 3;
        for (int i = 1; i < cycle.size(); i++)
        {
            int dir = prevDir + 1;
            while (true)
            {
                if (tryLayout((dir + 4) % 4, i))
                    break;
                dir--;

                //if smth goes wrong..
                //do an old (safe) strategy
                if (dir < 4)
                {
                    Logger.log("can't layout the path with length = " + cycle.size());

                    LayoutAlgo algo = new SingleCycleAlgo(cycle);
                    algo.setConstraints(bbGenerator);
                    algo.setData(cycle, similarity);
                    algo.run();

                    for (Word w : cycle)
                        wordPositions.put(w, algo.getWordRectangle(w));

                    return;
                }
            }
            prevDir = dir;
        }
    }

    private boolean tryLayout(int dir, int now)
    {
        int MAX = 10;

        SWCRectangle prevRec = rec[now - 1];

        if (dir == 0)
        {
            for (int i = 0; i < MAX; i++)
            {
                double x = prevRec.getMaxX();
                double y = prevRec.getMinY() + i * prevRec.getHeight() / MAX;
                if (canPlace(now, x, y))
                {
                    rec[now].move(x, y);
                    return true;
                }
            }

            for (int i = MAX - 1; i >= 0; i--)
            {
                double x = prevRec.getMaxX();
                double y = prevRec.getMinY() + i * prevRec.getHeight() / MAX - rec[now].getHeight();
                if (canPlace(now, x, y))
                {
                    rec[now].move(x, y);
                    return true;
                }
            }
        }
        else if (dir == 1)
        {
            for (int i = 0; i < MAX; i++)
            {
                double x = prevRec.getMinX() + i * prevRec.getWidth() / MAX;
                double y = prevRec.getMaxY();
                if (canPlace(now, x, y))
                {
                    rec[now].move(x, y);
                    return true;
                }
            }

            for (int i = MAX - 1; i >= 0; i--)
            {
                double x = prevRec.getMinX() + i * prevRec.getWidth() / MAX - rec[now].getWidth();
                double y = prevRec.getMaxY();
                if (canPlace(now, x, y))
                {
                    rec[now].move(x, y);
                    return true;
                }
            }
        }
        else if (dir == 2)
        {
            for (int i = 0; i < MAX; i++)
            {
                double x = prevRec.getMinX() - rec[now].getWidth();
                double y = prevRec.getMinY() + i * prevRec.getHeight() / MAX;
                if (canPlace(now, x, y))
                {
                    rec[now].move(x, y);
                    return true;
                }
            }

            for (int i = MAX - 1; i >= 0; i--)
            {
                double x = prevRec.getMinX() - rec[now].getWidth();
                double y = prevRec.getMinY() + i * prevRec.getHeight() / MAX - rec[now].getHeight();
                if (canPlace(now, x, y))
                {
                    rec[now].move(x, y);
                    return true;
                }
            }
        }
        else if (dir == 3)
        {
            for (int i = 0; i < MAX; i++)
            {
                double x = prevRec.getMinX() + i * prevRec.getWidth() / MAX;
                double y = prevRec.getMinY() - rec[now].getHeight();
                if (canPlace(now, x, y))
                {
                    rec[now].move(x, y);
                    return true;
                }
            }

            for (int i = MAX - 1; i >= 0; i--)
            {
                double x = prevRec.getMinX() + i * prevRec.getWidth() / MAX - rec[now].getWidth();
                double y = prevRec.getMinY() - rec[now].getHeight();
                if (canPlace(now, x, y))
                {
                    rec[now].move(x, y);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canPlace(int now, double x, double y)
    {
        SWCRectangle r = new SWCRectangle(x, y, rec[now].getWidth(), rec[now].getHeight());
        for (int i = 0; i < now; i++)
            if (rec[i].intersects(r))
                return false;
        return true;
    }

    private void generatePath()
    {
        int bestIndex = -1;
        double minWeight = Double.MAX_VALUE;

        int n = cycle.size();
        if (n <= 2)
            return;
        for (int i = 0; i < n; i++)
        {
            Word next = cycle.get((i + 1) % n);
            double weight = similarity.get(new WordPair(cycle.get(i), next));
            if (bestIndex == -1 || weight < minWeight)
            {
                minWeight = weight;
                bestIndex = i;
            }
        }

        assert (bestIndex != -1);
        List<Word> path = new ArrayList<Word>();
        for (int i = 0; i < n; i++)
            path.add(cycle.get((i + bestIndex + 1) % n));

        cycle = path;
    }

    private void generateBoundingBoxes()
    {
        wordPositions = new HashMap<Word, SWCRectangle>();
        for (Word w : cycle)
            wordPositions.put(w, bbGenerator.getBoundingBox(w, w.weight));

        rec = new SWCRectangle[cycle.size()];
        for (int i = 0; i < cycle.size(); i++)
            rec[i] = wordPositions.get(cycle.get(i));
    }

    @Override
    public SWCRectangle getWordRectangle(Word w)
    {
        return wordPositions.get(w);
    }

}
