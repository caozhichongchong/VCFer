package mapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// A SamReader reads .sam files and parses individual lines but doesn't associate related lines
public class SamReader implements SamProvider {
  public SamReader(BufferedReader reader, String path) {
    this.reader = reader;
    this.path = path;
  }

  public SequenceBuilder getNextSequence() throws IOException {
    while (true) {
      String line = this.getNextNonCommentLine();
      if (line == null) {
        this.done();
        return null;
      }
      SequenceBuilder sequenceBuilder;
      try {
        sequenceBuilder = this.parseLine(line);
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to parse sam line '" + line + "'");
      }
      if (sequenceBuilder != null)
        return sequenceBuilder;
    }
  }

  public boolean isReordered() {
    return false;
  }

  private void done() {
    int numWarnings = numSkippedSupplementalAlignments + numAlignmentsMissingQueryText;
    if (numWarnings > 0) {
      System.out.println("" + numWarnings + " warnings reading " + path);
    }
  }

  private String getNextNonCommentLine() throws IOException {
    while (true) {
      String line = this.reader.readLine();
      if (line == null)
        return line;
      if (line.startsWith("@"))
        continue;
      return line;
    }
  }

  private SequenceBuilder parseLine(String line) {
    SequenceBuilder sequenceBuilder = new SequenceBuilder();
    sequenceBuilder.setPath(this.path);

    String[] fields = line.split("\t");
    String name = fields[0];
    sequenceBuilder.setName(name);

    String samFlagString = fields[1];
    int samFlags = Integer.parseInt(samFlagString);
    boolean referenceReversed = (samFlags & 16) != 0;
    boolean isSupplementaryAlignment = (samFlags & 2048) != 0;
    // skipping supplementary alignments for now
    if (isSupplementaryAlignment) {
      if (numSkippedSupplementalAlignments < 1)
        System.out.println("Warning: skipping supplemental alignments, including " + line);
      numSkippedSupplementalAlignments++;
      return null;
    }

    String referenceContigName = fields[2];
    if ("*".equals(referenceContigName)) {
      // This indicates an unalighed query
      // We're not interested in them at the moment
      return null;
    }
    int spaceIndex = referenceContigName.indexOf(' ');
    if (spaceIndex != -1)
      referenceContigName = referenceContigName.substring(0, spaceIndex);
    int startPosition = Integer.parseInt(fields[3]);
    String cigarString = fields[5];

    String mateContigName = fields[6];
    boolean hasMate = !mateContigName.equals("*");
    boolean hasUnalignedMate = (samFlags & 8) != 0;
    boolean expectsMateAlignment = hasMate && !hasUnalignedMate;

    sequenceBuilder.asAlignment(referenceContigName, startPosition, cigarString, referenceReversed, expectsMateAlignment);

    String queryText = fields[9];
    if ("*".equals(queryText)) {
      if (numAlignmentsMissingQueryText < 1)
        System.out.println("Warning: skipping alignments having query text '" + queryText + "', including " + line);
      numAlignmentsMissingQueryText++;
      return null;
    }

    sequenceBuilder.add(queryText);

    return sequenceBuilder;
  }

  @Override
  public String toString() {
    return this.path;
  }

  BufferedReader reader;
  String path;

  int numSkippedSupplementalAlignments;
  int numAlignmentsMissingQueryText;
}
