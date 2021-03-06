package net.pixelcop.sewer.sink.durable;

import java.io.IOException;
import java.util.concurrent.BrokenBarrierException;

import net.pixelcop.sewer.Event;
import net.pixelcop.sewer.Sink;
import net.pixelcop.sewer.node.Node;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sink which simply rolls (closes/opens) it's subsink every X seconds (30 by default).
 *
 * @author chetan
 *
 */
public class RollSink extends Sink implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(RollSink.class);

  public static final String CONFIG_EVEN_BOUNDARIES = "sewer.sink.roll.even.boundaries";

  private static final int DEFAULT_ROLL_INTERVAL = 30;

  private Thread rollThread;
  private final long interval;

  private final boolean useEvenRollBoundaries;

  public RollSink(String[] args) {
    if (args == null || args.length == 0) {
      interval = DEFAULT_ROLL_INTERVAL * 1000;
    } else {
      interval = NumberUtils.toInt(args[0], DEFAULT_ROLL_INTERVAL) * 1000;
    }
    boolean even = Node.getInstance() != null ?
        Node.getInstance().getConf().getBoolean(CONFIG_EVEN_BOUNDARIES, false)
        : false;
    useEvenRollBoundaries = (even && interval % 30000 == 0);
  }

  @Override
  public void close() throws IOException {
    LOG.debug("close()");
    setStatus(CLOSING);

    this.subSink.close();
    this.rollThread.interrupt();
    try {
      this.rollThread.join();
    } catch (InterruptedException e) {
      LOG.warn("interrupted while waiting for rollThread to join");
    }
  }

  /**
   * Open the subsink and start the roll thread
   */
  @Override
  public void open() throws IOException {

    setStatus(OPENING);

    createSubSink();
    subSink.open();

    this.rollThread = new Thread(this);
    this.rollThread.setName("RollThread " + this.rollThread.getId());
    this.rollThread.start();

    setStatus(FLOWING);
  }

  @Override
  public void append(Event event) throws IOException {
    this.subSink.append(event);
  }

  @Override
  public void run() {

    while (getStatus() == FLOWING) {

      try {
        Thread.sleep(getSleepInterval());
        if (getStatus() != FLOWING) {
          LOG.debug("woke up and it looks like we aren't FLOWING anymore. quitting!");
          return;
        }
        rotate();

      } catch (InterruptedException e) {
        LOG.debug("interrupted while waiting to begin next rotation: " + e.getMessage());
        return;

      } catch (IOException e) {
        LOG.warn("rotation failed: " + e.getMessage());
        return;

      }
    }
  }

  /**
   * Calculate how long to sleep, taking even boundaries into account
   * @return
   */
  private long getSleepInterval() {
    if (!useEvenRollBoundaries) {
      return interval;
    }

    long r = interval % 60000 == 0 ? 60000 : 30000;
    long m = ((System.currentTimeMillis() + interval) % r);
    long t = interval - m;

    if (m < 20000 || interval == 30000) {
      return t;
    } else {
      return t + r;
    }
  }

  /**
   * Close old sink and open a new one
   *
   * @throws IOException
   * @throws BrokenBarrierException
   * @throws InterruptedException
   */
  private void rotate() throws IOException, InterruptedException {

    LOG.info("rotating sink");

    Sink newSink = getSinkFactory().build();
    newSink.open();

    setStatus(OPENING);

    Sink oldSink = subSink;
    this.subSink = newSink; // put new one in place

    // then start closing the old
    Thread.sleep(500);
    new SinkCloserThread(oldSink).start();


    setStatus(FLOWING);
    LOG.debug("rotation complete");
  }

  public long getInterval() {
    return interval;
  }

}
