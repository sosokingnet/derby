/*

   Derby - Class org.apache.derby.impl.jdbc.ClobUpdateableReader

   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License.

 */
package org.apache.derby.impl.jdbc;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.SQLException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.i18n.MessageService;
import org.apache.derby.iapi.services.sanity.SanityManager;

/**
 * <code>ClobUpdateableReader</code> is used to create a <code>Reader</code>
 * over a <code>LOBInputStream</code>.
 * <p>
 * This class is aware that the underlying stream can be modified and
 * reinitializes itself if it detects any change in the stream. This
 * invalidates the cache so the changes are reflected immediately.
 *
 * @see LOBInputStream
 */
final class ClobUpdateableReader extends Reader {
    
    /** Reader accessing the Clob data and doing the work. */
    private Reader streamReader;
    /** Character position of this reader. */
    private long pos;
    /** Underlying stream of byte data. */
    private InputStream stream;
    /** Connection object used to obtain synchronization-object. */
    private ConnectionChild conChild;
    /** flag to indicate if its associated with materialized clob */
    private boolean materialized;
    /** clob object this object is associated */
    private final EmbedClob clob;
    /**
     * Position in Clob where to stop reading.
     */
    private final long maxPos;
    
    
    /**
     * Constructs a <code>Reader</code> over a <code>LOBInputStream</code>.
     * @param stream underlying stream of byte data
     * @param conChild a connection object used to obtain synchronization-object
     * @throws IOException
     */
    ClobUpdateableReader (LOBInputStream stream, ConnectionChild conChild)
                                                        throws IOException {
        clob = null;
        materialized = true;
        this.conChild = conChild;
        this.stream = stream;
        init (stream, 0);
        //The subset of the Clob has not been requested. 
        //Hence set maxPos to -1.
        this.maxPos = -1;
    }

    /**
     * Constructs a <code>Reader</code> over a <code>LOBInputStream</code>.
     * @param clob EmbedClob this Reader is associated to.
     * @throws IOException
     * @throws SQLException
     */
    ClobUpdateableReader (EmbedClob clob) throws IOException, SQLException {
        materialized = clob.isWritable();        
        this.clob = clob;
        this.conChild = clob;
        //getting bytelength make some time leave exisitng streams
        //unusable
        long byteLength = clob.getByteLength();
        this.stream = clob.getInternalStream ();
        init (0, byteLength);
        //The subset of the Clob
        //has not been requested.
        //Hence set maxPos to -1.
        this.maxPos = -1;
    }
    
    /**
     * Construct an <code>ClobUpdateableReader<code> using the 
     * <code>EmbedClob</code> received as parameter. The initial
     * position in the stream is set to <code>pos</code> and the
     * stream is restricted to a length of <code>len</code>.
     *
     * @param clob EmbedClob this stream is associated with.
     * @param pos initial position. The position starts from 0.
     * @param len The length to which the underlying <code>InputStream</code>
     *            has to be restricted.
     * @throws IOException
     * @throws SQLException
     */
    ClobUpdateableReader (EmbedClob clob, long pos, long len) 
    throws IOException, SQLException {
        materialized = clob.isWritable();        
        this.clob = clob;
        this.conChild = clob;
        //Get the Byte length from the Clob which can be 
        //passes to the init method.
        long byteLength = clob.getByteLength();
        //Initialize the InputStream with the underlying 
        //InputStream of the Clob.
        this.stream = clob.getInternalStream ();
        //position the stream on pos using the init method.
        init (pos, byteLength);
        //The length requested cannot exceed the length
        //of the underlying Clob object. Hence chose the
        //minimum of the length of the underlying Clob
        //object and requested length.
        maxPos = Math.min(clob.length(), pos + len);
    }
        
    /**
     * Reads chars into the cbuf char array. Changes made in uderlying storage 
     * will be reflected immidiatly from the corrent position.
     * @param cbuf buffer to read into
     * @param off offet of the cbuf array to start writing read chars
     * @param len number of chars to be read
     * @return number of bytes read
     * @throws IOException
     */
    public int read(char[] cbuf, int off, int len) throws IOException {        
        updateIfRequired();
        
        //If maxPos is not invalid and the current position inside the 
        //stream has exceeded maxPos the read sould return -1 signifying
        //end of stream.
        if (maxPos != -1 && pos >= maxPos) {
            return -1;
        }

        int actualLength = 0;
        //If maxPos is not invalid then ensure that the length(len) 
        //that is requested falls within the restriction set by maxPos.
        if(maxPos != -1) {
            actualLength 
                    = (int )Math.min(len, maxPos - pos);
        }
        else {
            //maxPos has not been set. Make maxPos the length requested.
            actualLength = len;
        }
        int ret = streamReader.read (cbuf, off, actualLength);
        if (ret >= 0) {
            pos += ret;
        }
        return ret;
    }

    /**
     * Closes the reader.
     * @throws IOException
     */
    public void close() throws IOException {
        streamReader.close();
    }
    
    /**
     * Initializes the streamReader and skips to the given position.
     * @param skip number of characters to skip to reach initial position
     * @throws IOException if a streaming error occurs
     */
    private void init(LOBInputStream stream, long skip) 
                                                    throws IOException {
        streamReader = new UTF8Reader (stream, 0, stream.length (), 
                                        conChild, 
                                conChild.getConnectionSynchronization());
        long remainToSkip = skip;
        while (remainToSkip > 0) {
            long skipBy = streamReader.skip(remainToSkip);
            if (skipBy == 0) {
                if (streamReader.read() == -1) {
                    throw new EOFException (
                                 MessageService.getCompleteMessage (
                                 SQLState.STREAM_EOF, new Object [0]));
                }
                skipBy = 1;
            }
            remainToSkip -= skipBy;
        }
        pos = skip;
    }    

    private void init (long skip, long streamLength) throws IOException {
        streamReader = new UTF8Reader (stream, 0, streamLength,
                                        conChild, 
                                conChild.getConnectionSynchronization());
        long remainToSkip = skip;
        while (remainToSkip > 0) {
            long skipBy = streamReader.skip(remainToSkip);
            remainToSkip -= skipBy;
        }
        pos = skip;
    }

    /**
     * Updates the stream if underlying clob is modified since
     * this reader was created. 
     * If the stream is associated with a materialized clob, it 
     * checks if the underlying clob is updated since last read and 
     * updates itself if it is. If the stream is associated with 
     * non materialized clob and clob is materialized since last read it 
     * fetches the stream again and sets the position to current position.
     * @throws IOException
     */
    private void updateIfRequired () throws IOException {
        if (materialized) {
            LOBInputStream lobStream = (LOBInputStream) stream;
            if (lobStream.isObsolete()) {
                lobStream.reInitialize();
                init (lobStream, pos);
            }
        }
        else {
            //clob won't be null if the stream wasn't materialized
            //but still try to be safe
            if (SanityManager.DEBUG) {
                SanityManager.ASSERT (!(clob == null), 
                        "Internal error while updating stream");
            }
            if (clob.isWritable ()) {
                try {
                    stream = clob.getInternalStream();
                }
                catch (SQLException e) {
                    IOException ioe = new IOException (e.getMessage());
                    ioe.initCause (e);
                    throw ioe;
                }
                init ((LOBInputStream) stream, pos);
                materialized = true;
            }
        }
    }
}
