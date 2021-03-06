package com.github.eostermueller.xslt;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.NoSuchElementException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.xml.sax.InputSource;

import com.github.eostermueller.util.TextFileRepo;
import com.github.eostermueller.util.TextFileRepos;
import com.github.eostermueller.util.TextFileRepo.TextFileAndContents;
import com.github.eostermueller.util.TextFileRepos.OnePerFolder;

public class XsltSampler02 extends AbstractJavaSamplerClient {
	
	private static final String XSL_ROOT = "/xsl.root";
	/*
	 * Used to delimit parts of the key that will store the Transformer.
	 */
	private static final String PARM_XSLT_AND_FOLDER_ROOT = "xsl.root.parm";
	TextFileRepos repos = null;
	static KeyedPooledObjectFactory keyedPooledObjectFactory = null;
	static KeyedObjectPool<String,Transformer> keyedPool = null;
	
	/*
	 * (non-Javadoc)
	 * @see org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient#setupTest(org.apache.jmeter.protocol.java.sampler.JavaSamplerContext)
	 */
	@Override
	public void setupTest(JavaSamplerContext ctx) {
		File rootFolder_ = null;
		try {
			
			String rootFolder = null;
			if (ctx!=null) {
				rootFolder = ctx.getParameter(PARM_XSLT_AND_FOLDER_ROOT);
			}
			
			if (rootFolder!=null && rootFolder.trim().length()>0) {
				getLogger().info("Using xsl.root folder [" + rootFolder + "] from JMeter variable [" + PARM_XSLT_AND_FOLDER_ROOT + "]");
				rootFolder_ = new File(rootFolder);
				this.repos = new TextFileRepos(rootFolder_, TextFileRepos.OnePerFolder.XSL);
			} else {
				this.repos = new TextFileRepos(XsltSampler01.XSL_ROOT, TextFileRepos.OnePerFolder.XSL);
				getLogger().info("JMeter variable [" + PARM_XSLT_AND_FOLDER_ROOT + "] is ignored.");
			}
			getLogger().info("Process xsl and xml files from folder [" + this.repos.root.getAbsolutePath() + "] from classpath");
	        //Factory will get .xsl files from our home-baked test repository of xsl & xml files.
	        this.keyedPooledObjectFactory = new KeyedTransformerFactory(this.repos);
	        
	        //To configure min/max and other pool parameters, 
	        //create GenericKeyedObjectPoolConfig and pass as 2nd parm here:
	        this.keyedPool = new GenericKeyedObjectPool<String,Transformer>(this.keyedPooledObjectFactory);
	        			
			
		} catch (IOException e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			getLogger().error("Error reading xsl and xml files from disk. Stack trace:\n"+sw.toString(), e);
			getLogger().error("Message:\n"+e.getMessage());
		} catch (URISyntaxException e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			getLogger().error("Error reading xsl and xml files from disk. Stack trace:\n"+sw.toString(), e);
			getLogger().error("Message:\n"+e.getMessage());
		}
	}
    /* set up default arguments for the JMeter GUI
     * (non-Javadoc)
     * @see org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient#getDefaultParameters()
     */
    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(PARM_XSLT_AND_FOLDER_ROOT, null );
        return defaultParameters;
    }
	public SampleResult runTest(JavaSamplerContext ctx) {
    	
        SampleResult result = new SampleResult();
        result.sampleStart(); // start stopwatch    	
        StringBuilder sb = new StringBuilder();
        try {
			performXslt(this.repos, sb);
			result.setResponseData(sb.toString(), "UTF-8");
			result.setSuccessful(true);
			result.setResponseCodeOK();
			result.sampleEnd();//time for all transformations
		} catch (TransformerException e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			result.setResponseData(sw.toString(), "UTF-8");
			result.setSuccessful(false);
		}
		return result;
	}
	/**
	 * Serially, perform one tranformation for each .xml file in our little test repository of xml & xsl files.
	 * @param repos
	 * @param sb
	 * @throws TransformerException
	 */
	private static void performXslt(TextFileRepos repos, StringBuilder sb) throws TransformerException {
		
        for(TextFileRepo repo : repos.repos){

        	for(TextFileAndContents xml : repo.getXmlFiles()) {
                SAXSource saxSource = new SAXSource(new InputSource( new StringReader(xml.textFromFile) ));
                StringWriter writer = new StringWriter();
                
                Transformer transformer = null;
				try {
					transformer = keyedPool.borrowObject(repo.getName());
	    			transformer.transform(saxSource, new StreamResult(writer));
	    			sb.append("#Repo:" + repo.directory.getName() + " XMl file:" + xml.file.getName() + " XSL file: " + repo.getOnePerFolder().file.getName() + "\n");
	    			sb.append(writer.toString());
	    			sb.append("\n");
				} catch (Exception e) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					e.printStackTrace(pw);
					sb.append(sw.toString());
				} finally {
					try {
						keyedPool.returnObject(repo.getName(), transformer);
					} catch (Exception e) {
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						e.printStackTrace(pw);
						sb.append(sw.toString());
					}
				}
        	}
        }
	}
	public static File getDefaultRootFolder() throws URISyntaxException {
		URL resource = XsltSampler01.class.getResource(XSL_ROOT);
		return Paths.get(resource.toURI()).toFile();
	}
	
	public static void main(String args[]) throws IOException, TransformerException {
		File root = null;
		try {
			XsltSampler02 sampler = new XsltSampler02();
			sampler.setupTest(null);
				
			TextFileRepos repos = null;
			if (args.length == 1 && args[0] != null) {
				root = new File(args[0]);
				repos = new TextFileRepos(root,OnePerFolder.XSL);
			} else {
				repos = new TextFileRepos(XSL_ROOT,OnePerFolder.XSL);
			}
			
	        StringBuilder sb = new StringBuilder();
	        performXslt(repos, sb);
	        System.out.println(sb.toString());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
