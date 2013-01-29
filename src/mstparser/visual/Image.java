package mstparser.visual;

/**
 * This is a wrapper class for all the data types
 * associated with an image. Most of the actual data
 * is encapsulated inside the Poylgon objects.
 *
 * @author delliott
 *
 */

public class Image {
	
	public Polygon[] polygons;
	public double[] dimensions;
	public String filename;
	public String xmlFilename;
	public String dotFilename;
	
	public Image(String imageFile)
	{
		this.filename = imageFile;
	}
	
	public void setXMLFile(String filename)
	{
		this.xmlFilename = filename;
	}
	
	public void setDOTFile(String filename)
	{
		this.dotFilename = filename;
	}
	
	public void parseXMLFile()
	{
		if (this.xmlFilename != null)
		{
			
		}
	}

}
