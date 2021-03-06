package net.pixelcop.sewer.sink.durable;

import java.io.IOException;
import java.util.Date;

import net.pixelcop.sewer.Event;
import net.pixelcop.sewer.util.HdfsUtil;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Transaction {

  private static final Logger LOG = LoggerFactory.getLogger(Transaction.class);

  private final static FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyyMMdd-HHmmssSSSZ");

  /**
   * Unique ID for this Transaction. Used for reading/writing local buffers
   */
  private String id;

  /**
   * File extension to use for local buffer file
   */
  private String fileExt;

  /**
   * Destination sink path this Transaction will be written to
   */
  private String bucket;

  /**
   * Time (in ms) this Transaction was started
   */
  private long startTime;

  /**
   * Class name of Event Class used in this Transaction
   */
  private String eventClass;

  private boolean open = true;

  /**
   * Create new Transaction instance
   */
  public Transaction() {
  }

  /**
   * Create new Transaction instance
   *
   * @param clazz
   * @param bucket
   */
  public Transaction(Class<?> clazz, String bucket, String fileExt) {
    this.eventClass = clazz.getCanonicalName();
    this.bucket = bucket;
    this.fileExt = fileExt;

    this.id = generateId();
    this.startTime = System.currentTimeMillis();
  }

  /**
   * Create a unique ID for this Transaction
   *
   * <p>Formatted so that lexigraphical and chronological sorting is identical.</p>
   * <p><code>yyyyMMdd-HHmmssSSSZ.nanosec</code></p>
   *
   * @return
   */
  private String generateId() {
    return String.format("%s.%012d", DATE_FORMAT.format(new Date()), System.nanoTime());
  }

  public void rollback() {
    open = false;
    TransactionManager.getInstance().rollbackTx(this.id);
  }

  public void commit() {
    open = false;
    TransactionManager.getInstance().commitTx(this.id);
  }

  /**
   * Checks if this Transaction is active
   * @return boolean True if neither commit() nor rollback() have been called
   */
  public boolean isOpen() {
    return open;
  }

  /**
   * Path that local buffer will be written to
   *
   * @return Path
   */
  public Path createTxPath() {
    return new Path(createTxPath(true));
  }

  /**
   * Create Transaction path string with optional file extension
   *
   * @param includeExt Whether or not to include the file extension
   * @return String
   */
  public String createTxPath(boolean includeExt) {
    String path = "file://" + TransactionManager.getInstance().getWALPath() + "/" + this.id;
    if (includeExt) {
      path = path + this.getFileExt();
    }
    return path;
  }

  /**
   * Delete the files belonging to the given transaction id
   * @param id
   */
  public void deleteTxFiles() {

    if (LOG.isDebugEnabled()) {
      LOG.debug("Deleting files belonging to tx " + toString());
    }

    Path path = createTxPath();
    try {
      try {
        HdfsUtil.deletePath(path);
      } catch (InterruptedException e) {
        LOG.error("Interrupted while trying to delete local buffers for tx " + toString());
      }

    } catch (IOException e) {
      LOG.warn("Error deleting tx file: " + e.getMessage() + "\n    path:" + path.toString(), e);
    }

  }


  /**
   * Helper method for creating a new Event object from eventClass
   *
   * @return
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  public Event newEvent() throws Exception {
    return (Event) Class.forName(eventClass).newInstance();
  }

  @Override
  public String toString() {
    return this.id;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public String getEventClass() {
    return eventClass;
  }

  public void setEventClass(String eventClass) {
    this.eventClass = eventClass;
  }

  public void setFileExt(String fileExt) {
    this.fileExt = fileExt;
  }

  public String getFileExt() {
    return fileExt;
  }

}
