package edu.cloudy.main;

import edu.cloudy.layout.ContextPreservingAlgo;
import edu.cloudy.layout.LayoutAlgo;
import edu.cloudy.layout.LayoutResult;
import edu.cloudy.nlp.Word;
import edu.cloudy.nlp.WordPair;
import edu.cloudy.ui.WordCloudFrame;
import edu.cloudy.utils.Logger;
import edu.cloudy.utils.TimeMeasurer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * @author spupyrev 
 */
public class DotReader
{

    public static void main(String argc[])
    {
        Logger.doLogging = false;
        new DotReader().run();
    }

    private void run()
    {
        // 2. build similarities, words etc
        List<Word> words = new ArrayList<Word>();
        Map<WordPair, Double> similarity = new HashMap<WordPair, Double>();
        readDotFile("data/1101.dot", words, similarity);

        words = filterWords(words, similarity, 50);
        similarity = filterSimilarities(words, similarity);

        // 3. run a layout algorithm
        LayoutResult layout = runLayout(words, similarity);

        // 4. visualize it
        visualize(words, similarity, layout);
    }

    private List<Word> filterWords(List<Word> words, Map<WordPair, Double> similarity, int maxWords)
    {
        Collections.sort(words);
        Collections.reverse(words);

        if (maxWords > words.size())
            return words;

        return words.subList(0, maxWords);
    }

    private Map<WordPair, Double> filterSimilarities(List<Word> words, Map<WordPair, Double> similarity)
    {
        Map<WordPair, Double> similarityNew = new HashMap();
        for (WordPair wp : similarity.keySet())
        {
            if (words.contains(wp.getFirst()) && words.contains(wp.getSecond()))
            {
                similarityNew.put(wp, similarity.get(wp));
            }
        }
        return similarityNew;
    }

    private void readDotFile(String filename, List<Word> words, Map<WordPair, Double> similarity)
    {
        Map<String, Word> allWords = new HashMap();

        try
        {
            Scanner sc = new Scanner(new File(filename));
            System.out.println(sc.nextLine());
            System.out.println(sc.nextLine());

            while (sc.hasNext())
            {
                String line = sc.nextLine();
                if (line.contains("imp"))
                {
                    Word w = parseNode(line);
                    words.add(w);
                    allWords.put(w.word, w);

                    if (words.size() % 100 == 0)
                        System.out.println("read " + words.size() + " words");
                }
                else if (line.contains("sim"))
                {
                    parseEdge(line, allWords, similarity);

                    if (similarity.size() % 100 == 0)
                        System.out.println("read " + similarity.size() + " edges");
                }
                else
                    break;
            }

            sc.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void parseEdge(String line, Map<String, Word> allWords, Map<WordPair, Double> similarity)
    {
        String s1 = "";
        int i = 0;
        while (line.charAt(i) != '"')
            i++;
        i++;
        while (line.charAt(i) != '"')
        {
            s1 += line.charAt(i);
            i++;
        }
        i++;

        String s2 = "";
        while (line.charAt(i) != '"')
            i++;
        i++;
        while (line.charAt(i) != '"')
        {
            s2 += line.charAt(i);
            i++;
        }

        while (line.charAt(i) != 'm')
            i++;
        i++;
        i++;
        String sim = "";
        while (line.charAt(i) != ']')
        {
            sim += line.charAt(i);
            i++;
        }

        Word w1 = allWords.get(s1);
        Word w2 = allWords.get(s2);
        similarity.put(new WordPair(w1, w2), Double.parseDouble(sim));
    }

    private Word parseNode(String line)
    {
        String s1 = "";
        int i = 0;
        while (line.charAt(i) != '"')
            i++;
        i++;
        while (line.charAt(i) != '"')
        {
            s1 += line.charAt(i);
            i++;
        }

        String s2 = "";
        while (line.charAt(i) != '=')
            i++;
        i++;
        while (line.charAt(i) != ',')
        {
            s2 += line.charAt(i);
            i++;
        }

        return new Word(s1, Double.parseDouble(s2));
    }

    private LayoutResult runLayout(List<Word> words, Map<WordPair, Double> similarity)
    {
        LayoutAlgo algo = new ContextPreservingAlgo(words, similarity);

        return TimeMeasurer.execute(() -> algo.layout());
    }

    private void visualize(List<Word> words, Map<WordPair, Double> similarity, LayoutResult layout)
    {
        if (words.size() > 0 && words.size() <= 50)
            new WordCloudFrame(words, similarity, layout, null);
    }

}
