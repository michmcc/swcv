package de.tinloaf.cloudy.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Apr 25, 2013 
 * Extract star forest in three steps: 
 *  extract heaviest planar subgraph 
 *  run schnyder thing 
 *  partition each tree into odd-even stars
 */
public class StarForestExtractor {
	// todo: make setting
	private static boolean useSchnyder = false;

	private WordGraph g;

	public StarForestExtractor(WordGraph g) {
		this.g = g;
	}

	public List<StarForest> run() {
		List<StarForest> result = new ArrayList<StarForest>();

		// partition into 3 trees
		List<WordGraph> trees = extract3Trees();

		// odd-even stars
		for (WordGraph tree : trees)
			result.addAll(extractOddEvenForests(tree));
		
		return result;
	}

	private List<WordGraph> extract3Trees() {
		if (useSchnyder) {
			WordGraph planar = extractPlanarSubgraph(g);
			return extract3TreesSchnyder(planar);
		} else
			return extract3TreesGreedy((WordGraph) g.clone());
	}

	private WordGraph extractPlanarSubgraph(WordGraph wordGraph) {
		Set<Edge> mstEdges = new HashSet<Edge>();
		return new MaxSpanningTreeBuilder(wordGraph).getTree(mstEdges);
	}

	private List<WordGraph> extract3TreesSchnyder(WordGraph planar) {
		SchnyderTreeBuilder stb = new SchnyderTreeBuilder(planar, g);
		stb.run();
		List<WordGraph> trees = new ArrayList<WordGraph>();
		trees.add(stb.tree1);
		trees.add(stb.tree2);
		trees.add(stb.tree3);
		return trees;
	}

	private List<WordGraph> extract3TreesGreedy(WordGraph graph) {
		List<WordGraph> trees = new ArrayList<WordGraph>();

		//TODO: this can be anything
		int maxTrees = 3;
		for (int i = 0; i < maxTrees; i++) {
			if (!new CycleDetector(graph).hasCycle()) {
				trees.add(graph);
				break;
			}

			Set<Edge> mstEdges = new HashSet<Edge>();
			WordGraph mst = new MaxSpanningTreeBuilder(graph).getTree(mstEdges);
			trees.add(mst);
			graph.removeAllEdges(mstEdges);
		}

		return trees;
	}

	private List<StarForest> extractOddEvenForests(final WordGraph tree) {
		final Set<Edge> oddEdges = new HashSet<Edge>();
		final Set<Edge> evenEdges = new HashSet<Edge>();
		final Set<Vertex> taggedVertices = new HashSet<Vertex>();

		class TreeTagger {
			void tagVertex(Vertex v, boolean odd) {
				taggedVertices.add(v);

				for (Edge e : tree.edgesOf(v)) {
					if (!oddEdges.contains(e) && (!evenEdges.contains(e))) {
						if (odd)
							oddEdges.add(e);
						else
							evenEdges.add(e);

						tagVertex(tree.getOtherSide(e, v), !odd);
					}
				}
			}
		}

		for (Vertex v : tree.vertexSet())
			if (!taggedVertices.contains(v))
				new TreeTagger().tagVertex(v, false);

		WordGraph oddTree = (WordGraph) tree.clone();
		oddTree.removeAllEdges(evenEdges);
		WordGraph evenTree = (WordGraph) tree.clone();
		evenTree.removeAllEdges(oddEdges);

		List<StarForest> result = new ArrayList<StarForest>();
		result.add(new StarForest(oddTree.getComponents()));
		result.add(new StarForest(evenTree.getComponents()));

		return result;
	}

}
