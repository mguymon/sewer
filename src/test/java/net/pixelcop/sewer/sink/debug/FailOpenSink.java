package net.pixelcop.sewer.sink.debug;

import java.io.IOException;

import net.pixelcop.sewer.DrainSink;

@DrainSink
public class FailOpenSink extends NullSink {

  public FailOpenSink(String[] args) {
    super(args);
  }

  @Override
  public void open() throws IOException {
    setStatus(ERROR);
    throw new IOException("fail!");
  }
}
