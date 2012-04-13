/*
   Depth-first labyrinth generator using GraphStream.
   Copyright (C) 2011 fmdkdd <fmdkdd@gmail.com>

   This program is free software: you can redistribute it and/or
   modify it under the terms of the GNU General Public License as
   published by the Free Software Foundation, either version 3 of the
   License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/


import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import org.graphstream.algorithm.AStar;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Element;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.Layouts;
import org.graphstream.ui.swingViewer.GraphRenderer;
import org.graphstream.ui.swingViewer.View;
import org.graphstream.ui.swingViewer.Viewer;

/**
 * Create a visual layrinth using Graphstream. To use, just call :
 * new Laby(size);
 * to initialize the graph and display it inside its own frame. Press
 * space to toggle viewing the shortest path from the entry to the exit.
 */
public class Laby {
	protected static final String VIEW_NAME = "defaultView";
	protected static final int GRID = 0;
	protected static final int DIAMOND = 1;
	protected static final int CIRCLE = 2;

	protected Graph graph;
	protected int size;
	protected int style;
	protected List<Element> exitPath; 	// Shortest path to the exit.
	protected boolean pathHidden = true; 	// Whether the solution is
														// currently hidden in the
														// view.

	// CSS stylesheet for our labyrinth. Background is a subtle
	// off-white, edges (walls) are dark grey. Ninja edges are the same
	// color as the background, since Graphstream doesn't like setting
	// their size to 0. Nodes are the same width and color as edges, in
	// order to blend in.
	protected static final String css =
		"graph { fill-color: #efefe8; }" +
		"node { fill-color: #333333; size: 2px; }" +
		"edge { fill-color: #333333; size: 2px; }" +
		"node.path { fill-color: #ab4598; }" +
		"edge.path { fill-color: #ab4598; }" +
		"node.ninja { fill-color: #efefe8; }" +
		"edge.ninja { fill-color: #efefe8; }";

	public Laby(int size, int style) {
		this.size = size;
		this.style = style;

		// Override the default behavior of SingleGraph to fix the view
		// name since we need it later to add our keyListener. Its
		// default behavior is to add a view called "defaultView_id"
		// with id being a random number between 0 and 10000 each time
		// display() is called. Since we found no way of getting a hold
		// on this identifier string (beyond blindly looping all
		// possibilities), we replace it with our own identifier.
		graph = new SingleGraph("Daedalus") {
				public Viewer display(boolean autoLayout) {
					Viewer viewer = new Viewer(this,
					                           Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
					GraphRenderer renderer = Viewer.newGraphRenderer();

					viewer.addView(VIEW_NAME, renderer);

					if (autoLayout) {
						Layout layout = Layouts.newLayoutAlgorithm();
						viewer.enableAutoLayout(layout);
					}

					return viewer;
				}
			};

		// Turn the style up to 11.
		graph.addAttribute("ui.stylesheet", css);
		graph.addAttribute("ui.quality");
		graph.addAttribute("ui.antialias");

		createLabyrinth(size);

		// Let graphstream create the swing display.
		View view = graph.display(false).getView(VIEW_NAME);

		// And add our own keyListener to toggle showing the solution
		// when pressing space.
		view.requestFocus();
		view.addKeyListener(new KeyAdapter(){
				public void keyPressed(KeyEvent ev) {
					if (ev.getKeyCode() == KeyEvent.VK_SPACE) {
						for (Element e : exitPath)
							e.setAttribute("ui.class", pathHidden ? "path" : "ninja");
						pathHidden = !pathHidden;
					}
				}
			});
	}

	public void createLabyrinth(int size) {
		// First model the labyrinth as a graph where rooms are nodes,
		// and edges are doors between rooms. Each room has a door with
		// the four rooms it shares a wall with (Von Neumann neighbours).

		// Create the size*size nodes on a grid.
		for (int i=0; i < size; ++i) {
			for (int j=0; j < size; ++j) {
				Node n = graph.addNode(i+","+j);
				n.addAttribute("x", i);
				n.addAttribute("y", j);
			}
		}

		// Add an edge for each Von Neumann neighbour node. Since we go
		// through each node, we only have to add two neighbours
		// (edges), otherwise we will add already existing edges.
		for (int i=0; i < size; ++i) {
			for (int j=0; j < size; ++j) {
				// Transform the coordinates to a node id.
				String id = i+","+j;

				// Add an edge to the node to the right, if there is one.
				String nextid = (i+1)+","+j;
				if (graph.getNode(nextid) != null)
					graph.addEdge(id + " -> " + nextid, id, nextid).getId();

				// Add an edge to the node on the bottom, if there is one.
				nextid = i+","+(j+1);
				if (graph.getNode(nextid) != null)
					graph.addEdge(id + " -> " + nextid, id, nextid).getId();
			}
		}

		// Now the the labyrinth is a boring grid. We need an entry and
		// an exit. To make it more challenging, we pick opposite corners.

		// Add the start node to the left of the origin.
		Node bottom_left = graph.getNode(0 + "," + 0);
		Node entry = graph.addNode("-1,0");
		entry.addAttribute("x", -1);
		entry.addAttribute("y", 0);
		graph.addEdge(bottom_left.getId() + " -> " + entry.getId(),
		              bottom_left.getId(), entry.getId());

		// Add the exit node to the right of the upper-right corner.
		Node upper_right = graph.getNode((size-1) + "," + (size-1));
		Node exit = graph.addNode(size + "," + (size-1));
		exit.addAttribute("x", size);
		exit.addAttribute("y", (size-1));
		graph.addEdge(upper_right.getId() + " -> " + exit.getId(),
		              upper_right.getId(), exit.getId());

		// Now, all doors are closed. How do we go through the maze ? We
		// need to open some doors (remove edges), but if we remove too
		// much it will be too easy to find the exit. A spanning tree
		// will ensure there is only one path from the entry to the
		// exit, and that all rooms are reachable. We use a depth first
		// walk since it gives challenging labyrinths even for
		// moderate sizes.
		depthFirstWalk(graph, entry.getId(), "tree");

		// We also need to know the exact path to the exit. AStar is
		// somewhat overkill but won't take long. But first, we get rid
		// of all edges that are not part of the tree, since we don't
		// need them anymore. We also keep a record of those that are in
		// the tree.
		Stack<String> toPrune = new Stack<String>();
		List<String> treeEdges = new LinkedList<String>();
		for (Edge e : graph.getEdgeSet()) {
			if (!e.hasAttribute("tree"))
				toPrune.push(e.getId());
			else
				treeEdges.add(e.getId());
		}

		while (!toPrune.isEmpty())
			graph.removeEdge(toPrune.pop());

		// Now compute the path from the entry to the exit.
		AStar astar = new AStar(graph);
		astar.compute(entry.getId(), exit.getId());

		// Store the solution path and hide it now, to display it later.
		exitPath = new LinkedList<Element>();
		for (Element e : astar.getShortestPath().getEdgePath()) {
			e.addAttribute("solution");
			e.setAttribute("ui.class", "ninja");
			exitPath.add(e);
		}

		// Always show the entry and exit edges.
		exitPath.remove(0).setAttribute("ui.class", "path");
		int len = exitPath.size();
		exitPath.remove(len-1).setAttribute("ui.class", "path");

		// We also add the node to the path, to avoid dirty white dots
		// in the display.
		for (Element e : astar.getShortestPath().getNodePath()) {
			e.setAttribute("ui.class", "ninja");
			exitPath.add(e);
		}

		// Always show the entry and exit nodes.
		exitPath.remove(len-1).setAttribute("ui.class", "path");
		exitPath.remove(exitPath.size()-1).setAttribute("ui.class", "path");

		// Now we need to turn the graph inside out : nodes should not
		// be rooms anymore, but room corners, and edges should be walls
		// between rooms.

		// Create a new grid of walls around the rooms of the previous
		// model. First create the corners (nodes).
		for (int i=0; i < size+1; ++i) {
			for (int j=0; j < size+1; ++j) {
				// Offset by 0.5 to have the walls around the rooms, and
				// add noise to give a nice "doodle" effect, if desired.
				double x = i - .5;
				double y = j - .5;
				if (style == GRID) {
					x += Math.random() / 4;
					y += Math.random() / 4;
				}

				Node n = graph.addNode(i+";"+j);
				n.addAttribute("x", x);
				n.addAttribute("y", y);
			}
		}

		// Now add the walls (edges).
		for (int i=0; i < size+1; ++i) {
			for (int j=0; j < size+1; ++j) {
				// Id of the corresponding corner.
				String id = i+";"+j;

				// Add an edge to the corner to the right .
				String nextid = (i+1)+";"+ j;
				if (graph.getNode(nextid) != null)
					graph.addEdge(id + " -> " + nextid, id, nextid);

				// Add an edge to the bottom corner.
				nextid = i+";"+(j+1);
				if (graph.getNode(nextid) != null)
					graph.addEdge(id + " -> " + nextid, id, nextid);
			}
		}

		// Then, we must remove each wall of this new model that is
		// overlapping a door that is not a part of the spanning tree in
		// the previous model.
		Node n0, n1;
		int x0, y0, x1, y1;
		for (String edge : treeEdges) {
			// Extract the coordinates of each node to map the
			// corresponding wall in the new model.
			n0 = graph.getEdge(edge).getNode0();
			x0 = (int)n0.getNumber("x");
			y0 = (int)n0.getNumber("y");

			n1 = graph.getEdge(edge).getNode1();
			x1 = (int)n1.getNumber("x");
			y1 = (int)n1.getNumber("y");

			// The wall (edge in the new model) above the closed door
			// (edge in the previous model) that went from room x0,y0
			// to room x1,y1, will go from room corner x1;y1 to room
			// corner (x0+1);(y0+1).
			String wall = x1+";"+y1 + " -> " + (x0+1)+";"+(y0+1);
			graph.removeEdge(wall);

			// Remove the old model edge as well if it's not part of the
			// solution path.
			if (!graph.getEdge(edge).hasAttribute("solution"))
				graph.removeEdge(edge);
		}

		// Now, the transition to the new model is nearly complete. We
		// still need to remove two walls : those that cross the doors
		// (entry -> entryway) and (exit -> exitway).

		// Extract coordinates, same as above.
		{
			String entryEdge = entry.getEdge(0).getId();
			n0 = graph.getEdge(entryEdge).getNode0();
			x0 = (int)n0.getNumber("x");
			y0 = (int)n0.getNumber("y");

			n1 = graph.getEdge(entryEdge).getNode1();
			x1 = (int)n1.getNumber("x");
			y1 = (int)n1.getNumber("y");

			String wall = x0+";"+y0 + " -> " + (x1+1)+";"+(y1+1);
			graph.removeEdge(wall);
		}

		// The exit edge now.
		{
			String exitEdge = exit.getEdge(0).getId();
			n0 = graph.getEdge(exitEdge).getNode0();
			x0 = (int)n0.getNumber("x");
			y0 = (int)n0.getNumber("y");

			n1 = graph.getEdge(exitEdge).getNode1();
			x1 = (int)n1.getNumber("x");
			y1 = (int)n1.getNumber("y");

			String wall = (x0+1)+";"+y0 + " -> " + x1+";"+(y1+1);
			graph.removeEdge(wall);
		}

		// That's it ! We just clean up by getting rid of all the nodes
		// of the old model that are not part of the solution.
		LinkedList<String> leftovers = new LinkedList<String>();
		for (Node n : graph)
			if (n.getDegree() == 0)
				leftovers.add(n.getId());
		for (String s : leftovers)
			graph.removeNode(s);

		// Or ... we can have a litte fun by using various
		// transformations of the grid !

		// Why not try the circle labyrinth today ?
		// First, center around the origin.
		for (Node n : graph) {
			double x = n.getNumber("x");
			double y = n.getNumber("y");

			// Offset by .5 (see the creation of the second model above).
			n.setAttribute("x", x - (size/2.)+.5);
			n.setAttribute("y", y - (size/2.)+.5);
		}

		if (style == DIAMOND) {
			// Diamonds are a geek's best .. friend ?
			double rot = Math.PI/4;
			for (Node n : graph) {
				double x = n.getNumber("x");
				double y = n.getNumber("y");

				n.setAttribute("x", (x * Math.cos(rot) + y * Math.sin(rot)));
				n.setAttribute("y", (-x * Math.sin(rot) + y * Math.cos(rot)));
			}
		}

		if (style == CIRCLE) {
			// Then, use polar coordinates.
			for (Node n : graph) {
				double x = n.getNumber("x");
				double y = n.getNumber("y");
				// Use max(|x|,|y|) to get a circle from a square, and
				// euclidean distance to transform absolutely nothing.
				double r = Math.max(Math.abs(x), Math.abs(y));
				double theta = Math.atan2(y,x);

				n.setAttribute("x", r * Math.cos(theta));
				n.setAttribute("y", r * Math.sin(theta));
			}
		}
	}

	public static void depthFirstWalk(Graph g, String sourceId, String treeFlag) {
		// Create a spanning tree of the graph by doing a depth first walk.

		// Stacks ... smells like depth first alright.
		Stack<Node> nodeStack = new Stack<Node>();
		Stack<Edge> edgeStack = new Stack<Edge>();
		List<Edge> nextEdges = new ArrayList<Edge>();

		Edge last = null;
		Node source = g.getNode(sourceId);
		nodeStack.push(source);

		while (!nodeStack.isEmpty()) {
			if (!edgeStack.isEmpty())
				last = edgeStack.pop();

			Node v = nodeStack.pop();
			if (!v.hasAttribute("marked")) {
				v.addAttribute("marked");

				if (last != null)
					last.addAttribute(treeFlag);

				// (pseudo-) Randomize the leaving edges to create
				// (pseudo-) variety at each execution.
				nextEdges.clear();
				for (Edge e : v.getLeavingEdgeSet())
					nextEdges.add(e);
				Collections.shuffle(nextEdges);

				for (Edge e : nextEdges) {
					Node u = e.getOpposite(v);
					if (!u.hasAttribute("marked")) {
						nodeStack.push(u);
						edgeStack.push(e);
					}
				}
			}
		}
	}

	public static void main(String args[]) {
		if (!(args.length == 1 || args.length == 2)) {
			System.out.println("Usage : java Laby SIZE [STYLE]");
			System.out.println("Generate a square labyrinth of width SIZE.");
			System.out.println("STYLE is a char in [d,c], where :");
			System.out.println(" - 'd' generates a diamond-shaped labyrinth.");
			System.out.println(" - 'c' generates a (squished) circle-shaped labyrinth.");
			System.out.println();
			System.out.println("Press space to display the solution.");
		} else {
			int style = GRID;
			if (args.length == 2) {
				if ("d".equals(args[1]))
					style = DIAMOND;
				else if ("c".equals(args[1]))
					style = CIRCLE;
				else
					style = GRID;
			}
			Laby l = new Laby(Integer.parseInt(args[0]), style);
		}
	}
}
