package com.bryanreinero.dsvloader;

//import com.bryanreinero.firehose.Firehose;
import com.bryanreinero.firehose.Converter;
import com.bryanreinero.firehose.Transformer;
import com.bryanreinero.firehose.cli.CallBack;
//import com.bryanreinero.firehose.cli.CommandLineInterface;
//import com.bryanreinero.firehose.cli.OptionFactory;
import com.bryanreinero.firehose.metrics.Interval;
import com.bryanreinero.firehose.metrics.SampleSet;

import com.bryanreinero.util.Application;
import com.bryanreinero.util.DAO;
import com.bryanreinero.util.WorkerPool;
import com.bryanreinero.util.WorkerPool.Executor;
import com.bryanreinero.util.Printer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.mongodb.DBObject;

public class DSVLoader implements Executor {
	
	private static final String appName = "DSVLoader";
	private final Application worker;
	private final SampleSet samples;
	private AtomicInteger linesRead = new AtomicInteger(0);
	private Converter converter = new Converter();
	private BufferedReader br = null;
	private DAO dao = null;
	
	private static Boolean verbose = false;
	private String filename = null;
	
	public DSVLoader ( String[] args ) throws Exception {
		
		Map<String, CallBack> myCallBacks = new HashMap<String, CallBack>();
		
        /* dmf: parse header from first line of file
		// custom command line callback for csv conversion
		myCallBacks.put("h", new CallBack() {
			@Override
			public void handle(String[] values) {
				for (String column : values) {
					String[] s = column.split(":");
					converter.addField( s[0], Transformer.getTransformer( s[1] ) );
				}
			}
		});
		
		// custom command line callback for delimeter
		myCallBacks.put("d", new CallBack() {
			@Override
			public void handle(String[] values) {
				converter.setDelimiter( values[0].charAt(0) );
			}
		});
        */

		// custom command line callback for delimeter
		myCallBacks.put("f", new CallBack() {
			@Override
			public void handle(String[] values) {
				filename  = values[0];
				try { 
					br = new BufferedReader(new FileReader(filename));

                    // read header line from file
                    String ln = br.readLine();
                    String colDelim = ln.substring(0,1);
                    String fieldDelim = ln.substring(1,2);
                    String header = ln.substring(2);
                    converter.setDelimiter( colDelim.charAt(0) );
                    for (String column : header.split(colDelim)) {
                    String[] s = column.split(fieldDelim);
                    converter.addField(s[0], Transformer.getTransformer(s[1]));
                    }
                
				}catch (Exception e) {
                    System.out.println("Caught exception in file handler:"
                                       +e.getMessage());
                    if (verbose)
                        e.printStackTrace();
                    System.out.println("Exiting.");
                    System.exit(-1);
				}
			}
		});

        // Verbose
		myCallBacks.put("v", new CallBack() {
			@Override
			public void handle(String[] values) {
				verbose = true;
			}
		});

		worker = Application.ApplicationFactory.getApplication( appName, this, args,
				myCallBacks);
		samples = worker.getSampleSet();
		dao = worker.getDAO("test", "dsvload");
		worker.addPrintable(this);
		worker.start();
	}
	
    @Override
    public void execute() {
        String currentLine = null;
        Interval total = samples.set("total");
        try {
        	
        	// read the next line from source file
        	Interval readLine = samples.set("readline");
        	synchronized ( br ) {
            	currentLine = br.readLine();
        	}
            readLine.mark();
            
            if ( currentLine == null )
                worker.stop();
             
            else {
                linesRead.incrementAndGet();
                
                // Create the DBObject for insertion
                Interval build = samples.set("build");
                DBObject object = converter.convert( currentLine );
                build.mark();
                
                // Insert the DBObject
                Interval insert = samples.set("insert");
                dao.insert( object );
                insert.mark();
                
                total.mark();
   
            }
        } catch (IOException e) {
        	worker.stop();
            e.printStackTrace();
            
            try {
            	synchronized ( br ) {
                	if (br != null)br.close();
            	}
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
	
	@Override 
	public String toString() {
		StringBuffer buf = new StringBuffer("{ ");
		buf.append("threads: "+worker.getNumThreads() );
		buf.append(", \"lines read\": "+ this.linesRead );
		buf.append(", samples: "+ samples );
		
		if( verbose ) {
			buf.append(", converter: "+converter);
			buf.append(", dao: "+dao);
			buf.append(", source: "+filename);
		}
		buf.append(" }");
		return buf.toString();
	}
    
    public static void main( String[] args ) {
    	
    	try {
    		new DSVLoader( args );
		} 
		catch (Exception e) {
            System.out.println("Caught exception in main:"+e.getMessage());
            if (verbose)
                e.printStackTrace();
            System.out.println("Exiting from main.");
			System.exit(-1);
		}
    }
}
