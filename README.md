# Laby-o-matic

This is a labyrinth generator in Java using the [GraphStream][]
library for dynamic graphs.

![Screenshot](https://raw.githubusercontent.com/fmdkdd/laby-o-matic/master/doc/laby-hell.png)

## Usage

Compile the source with `make` then run with `./run.sh Laby SIZE
[STYLE]` to generate a square labyrinth of width SIZE.  STYLE is an
optional char in [d,c]. 'd' generates a diamond-shaped labyrinth and
'c' generates a (squished) circle-shaped labyrinth.

Press space in the labyrinth window to display the solution.

[GraphStream]: http://graphstream-project.org/
