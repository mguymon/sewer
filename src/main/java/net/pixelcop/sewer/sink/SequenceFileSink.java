package net.pixelcop.sewer.sink;

import java.io.IOException;

import net.pixelcop.sewer.DrainSink;
import net.pixelcop.sewer.Event;
import net.pixelcop.sewer.node.Node;
import net.pixelcop.sewer.util.HdfsUtil;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.io.VLongWritable;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple wrapper around {@link FSDataOutputStream} with compression
 *
 * @author chetan
 *
 */
@DrainSink
public class SequenceFileSink extends BucketedSink {

  private static final Logger LOG = LoggerFactory.getLogger(SequenceFileSink.class);

  private static final VLongWritable ONE = new VLongWritable(1L);

  /**
   * Configured DFS path to write to
   */
  protected String configPath;

  /**
   * Reference to DFS Path object
   */
  protected Path dstPath;

  protected Writer writer;

  public SequenceFileSink(String[] args) {
    this.configPath = args[0];
  }

  @Override
  public void close() throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("close() called; currently: " + getStatusString());
      LOG.debug("Closing: " + HdfsUtil.pathToString(dstPath));
    }

    if (writer != null) {
      writer.close();
    }
    nextBucket = null;
    setStatus(CLOSED);

    if (LOG.isInfoEnabled()) {
      LOG.info("Closed: " + HdfsUtil.pathToString(dstPath));
    }
  }

  @Override
  public void open() throws IOException {
    LOG.debug("open");
    setStatus(OPENING);
    if (nextBucket == null) {
      generateNextBucket();
    }
    createWriter();
    setStatus(FLOWING);
  }

  protected void createWriter() throws IOException {

    Configuration conf = Node.getInstance().getConf();

    CompressionCodec codec = HdfsUtil.createCodec();
    dstPath = new Path(nextBucket + ".seq");
    FileSystem hdfs = dstPath.getFileSystem(conf);

    writer = SequenceFile.createWriter(
        hdfs, conf, dstPath,
        Node.getInstance().getSource().getEventClass(),
        VLongWritable.class,
        CompressionType.BLOCK, codec);

    if (LOG.isInfoEnabled()) {
      LOG.info("Opened: " + HdfsUtil.pathToString(dstPath));
    }

    nextBucket = null;
  }

  @Override
  public String getFileExt() {
    return ".seq";
  }

  @Override
  public String generateNextBucket() {
    nextBucket = BucketPath.escapeString(configPath);
    return nextBucket;
  }

  @Override
  public void append(Event event) throws IOException {
    writer.append(event, ONE);
  }

}
