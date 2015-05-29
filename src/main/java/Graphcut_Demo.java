import graphcut.GraphCut;
import graphcut.Terminal;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.plugin.filter.PlugInFilter;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * A tutorial demo for demonstrating Fiji's graphcut plugin usage.
 *
 * @author Yili Zhao
 */
public class Graphcut_Demo implements PlugInFilter {
	protected ImagePlus imageA;
	protected ImagePlus imageB;
	protected ImagePlus no_graphcut;
	protected ImagePlus graphcut;

	// image property members
	private int width;
	private int height;
	private int cwidth;
	private int cheight;

	/**
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		imageA = imp;
		
		return DOES_RGB;
	}

	/**
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor ip) {
		// get width and height
		width = ip.getWidth();
		height = ip.getHeight();
		
		imageB = imageA.duplicate();
		
		// compute overlapped width
		int overlap_width = width / 2;
		
		// compute column offset
		int xoffset = width - overlap_width;
		
		// compute composite width and height
		cwidth = 2 * width - overlap_width;
		cheight = height;
		
		no_graphcut = NewImage.createRGBImage("Direct composite", cwidth, cheight, 1, NewImage.FILL_BLACK);
		
		// copy imageA and ImageB to no_graphcut
		ColorProcessor cp1 = (ColorProcessor)imageA.getProcessor();
		ColorProcessor cp2 = (ColorProcessor)imageB.getProcessor();
		ColorProcessor cp3 = (ColorProcessor)no_graphcut.getProcessor();
		
		int[] p1 = (int[])cp1.getPixels();
		int[] p2 = (int[])cp2.getPixels();
		int[] p3 = (int[])cp3.getPixels();
		
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				p3[x + y * cwidth] = p1[x + y * width];
			}
		}
		
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				p3[x + y * cwidth + xoffset] = p2[x + y * width];
			}
		}
		
		// estimated node count
		int est_nodes = height * overlap_width;
		// estimated edges
		int est_edges = est_nodes * 4;
		
		// create graphcut object
		GraphCut g = new GraphCut(est_nodes, est_edges);
		
		// set the source/sink weights
		for (int y = 0; y < height; y++) {
			g.setTerminalWeights(y * overlap_width, Integer.MAX_VALUE, 0);
			g.setTerminalWeights(y * overlap_width + overlap_width - 1, 0, Integer.MAX_VALUE);
		}
		
		
		int[] v1 = {0, 0, 0};
		int[] v2 = {0, 0, 0};
		int pos1, pos2;
		float cap0, cap1, cap2;
		
		// set edge weights
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < overlap_width; x++) {
				int idx = y * overlap_width + x;
				
				pos1 = y * width + xoffset + x;
				pos2 = y * width + x;
				
				v1[0] = (int)(p1[pos1] & 0xff0000) >> 16;
			    v1[1] = (int)(p1[pos1] & 0x00ff00) >> 8;
		        v1[2] = (int)(p1[pos1] & 0x0000ff);
		        
			    v2[0] = (int)(p2[pos2] & 0xff0000) >> 16;
			    v2[1] = (int)(p2[pos2] & 0x00ff00) >> 8;
			    v2[2] = (int)(p2[pos2] & 0x0000ff);
			    
			    cap0 = L2norm(v1, v2);
			    
			    // add right edge
			    if (x + 1 < overlap_width) {
			    	pos1 = y * width + xoffset + x + 1;
					pos2 = y * width + x + 1;
					
					v1[0] = (int)(p1[pos1] & 0xff0000) >> 16;
				    v1[1] = (int)(p1[pos1] & 0x00ff00) >> 8;
			        v1[2] = (int)(p1[pos1] & 0x0000ff);
			        
				    v2[0] = (int)(p2[pos2] & 0xff0000) >> 16;
				    v2[1] = (int)(p2[pos2] & 0x00ff00) >> 8;
				    v2[2] = (int)(p2[pos2] & 0x0000ff);
				    
				    cap1 = L2norm(v1, v2);
				    
				    g.setEdgeWeight(idx, idx + 1, cap0 + cap1);
			    }
			    
			    // add bottom edge
			    if (y + 1 < height) {
			    	pos1 = (y + 1) * width + xoffset + x;
					pos2 = (y + 1) * width + x;
					
					v1[0] = (int)(p1[pos1] & 0xff0000) >> 16;
				    v1[1] = (int)(p1[pos1] & 0x00ff00) >> 8;
			        v1[2] = (int)(p1[pos1] & 0x0000ff);
			        
				    v2[0] = (int)(p2[pos2] & 0xff0000) >> 16;
				    v2[1] = (int)(p2[pos2] & 0x00ff00) >> 8;
				    v2[2] = (int)(p2[pos2] & 0x0000ff);
				    
				    cap2 = L2norm(v1, v2);
				    
				    g.setEdgeWeight(idx, idx + overlap_width, cap0 + cap2);
			    }
			}
		}
		
		// compute min-cut by max-flow
		g.computeMaximumFlow(false, null);
		
		graphcut = no_graphcut.duplicate();
		graphcut.setTitle("Graphcut composite");
		ColorProcessor cp = (ColorProcessor)graphcut.getProcessor();
		int[] p = (int[])cp.getPixels();
		
		// find label for every pixel in overlapped region
		int idx = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < overlap_width; x++) {
				if (g.getTerminal(idx) == Terminal.FOREGROUND) {
					p[y * cwidth + xoffset + x] = p1[y * width + xoffset + x];
				} else if (g.getTerminal(idx) == Terminal.BACKGROUND) {
					p[y * cwidth + xoffset + x] = p2[y * width + x];
				}
				idx++;
			}
		}
		
		no_graphcut.show("Composite without graphcut");
		graphcut.show("Composite with graphcut");
	}
	
	private float L2norm(int[] v1, int[] v2) {
		float norm = (float)Math.sqrt((v1[0] - v2[0]) * (v1[0] - v2[0]) +
				         (v1[1] - v2[1]) * (v1[1] - v2[1]) +
				         (v1[2] - v2[2]) * (v1[2] - v2[2]));
		return norm;
	}

	public void showAbout() {
		IJ.showMessage("GraphcutDemo",
			"a tutorial for demonstrating graphcut usage"
		);
	}

	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads an
	 * image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> clazz = Graphcut_Demo.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		// open the Clown sample
		ImagePlus image = IJ.openImage("http://cs2.swfc.edu.cn/~zyl/wp-content/uploads/2015/05/strawberry.jpg");
		image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
}
