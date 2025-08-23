package com.bhmsware.jzip;

import java.io.*;

public class JZip {
	public static final int[] SUPPORTED_COMPRESSION_METHODS = { 0, 8 };

	protected DataInputStream inStream;
	protected int numCD;
	protected int offsetCD;

	public JZip(InputStream is) throws IOException {
		inStream = new DataInputStream(is);
		int size = inStream.available();

		int skip = size - 22;
		byte[] buffer = new byte[256];

		// searching for EOCD
		do {
			if((skip -= 234) < 0) skip = 0;

			inStream.mark(0);
			inStream.skip(skip);
			inStream.read(buffer, 0, size >= 256 ? 256 : size);
			inStream.reset();

			// yanderedev moment yah
			for(int i = 238; i > 3;) {

				if(buffer[--i] == 0x06) {
					if(buffer[--i] == 0x05) {
						if(buffer[--i] == 'K') {
							if(buffer[--i] == 'P') {
								readEndOfCentralDirectory(buffer, i);
								return;
							}
						}
					}
				}

			}
		} while(skip > 0);

		throw new IllegalArgumentException("JZip: EOCD not found");
	}

	protected void readEndOfCentralDirectory(byte[] buffer, int skipToEOCD) {
		// signature (4 bytes)
		// disk num (2 bytes)
		// start disk num (2 bytes)
		// CD num in disk (2 bytes)
		numCD = (buffer[skipToEOCD + 8] & 0xff) | 
			((buffer[skipToEOCD + 9] & 0xff) << 8);
		// total CD num (2 bytes)
		// CD size (4 bytes)
		// CD offset (4 bytes)
		offsetCD = (buffer[skipToEOCD + 16] & 0xff) | 
			((buffer[skipToEOCD + 17] & 0xff) << 8) | 
			((buffer[skipToEOCD + 18] & 0xff) << 16) | 
			((buffer[skipToEOCD + 19] & 0xff) << 24);
		// comment length (2 bytes)
		// comment
	}

	public byte[] extractFile(String path) throws Exception {
		byte compressed[];
		byte uncompressed[];

		inStream.mark(0);
		inStream.skip(offsetCD);
		for(int i = 0; i < numCD; i++) {
			// CD signature
			if(inStream.readInt() != 0x504b0102)
				throw new IllegalArgumentException("JZip: " + path + ": Bad CD signature");
			
			byte[] structCD = new byte[42];

			inStream.read(structCD);

			// reading filename first
			byte[] bFilename = new byte[(structCD[24] & 0xff) | 
				((structCD[25] & 0xff) << 8)];
			inStream.read(bFilename);

			if(new String(bFilename).equals(path)) {

				// check compression method
				int compMethod = (structCD[6] & 0xff) | ((structCD[7] & 0xff) << 8);
				if(!checkCompMethod(compMethod))
					throw new IllegalArgumentException("JZip: " + path + ": Unsupported compression method");

				// allocate memory
				compressed = new byte[(structCD[16] & 0xff) | 
					((structCD[17] & 0xff) << 8) | 
					((structCD[18] & 0xff) << 16) | 
					((structCD[19] & 0xff) << 24)];
				uncompressed = new byte[(structCD[20] & 0xff) | 
					((structCD[21] & 0xff) << 8) | 
					((structCD[22] & 0xff) << 16) | 
					((structCD[23] & 0xff) << 24)];
				
				// LFH
				inStream.reset();
				inStream.mark(0);
				// offset to LFH
				inStream.skip(((structCD[38] & 0xff) | 
							((structCD[39] & 0xff) << 8) | 
							((structCD[40] & 0xff) << 16) | 
							((structCD[41] & 0xff) << 24)));
				// LFH signature
				if(inStream.readInt() != 0x504b0304)
					throw new IllegalArgumentException("JZip: " + path + ": Bad LFH signature");
				// skip useless LFH stuff
				inStream.skip(22);
				inStream.skip(
						// filename length
						(inStream.readUnsignedByte() | 
						 (inStream.readUnsignedByte() << 8)) + 
						// extra field length
						(inStream.readUnsignedByte() | 
						 (inStream.readUnsignedByte() << 8)));
				inStream.read(compressed);
				inStream.reset();

				// decode now
				if(!decodeData(uncompressed, compressed, compMethod))
					throw new IllegalArgumentException("JZip: " + path + ": invalid compressed data to inflate");

				return uncompressed;

			} else {

				// not our entry we're looking for
				inStream.skip((structCD[26] & 0xff) | ((structCD[27] & 0xff) << 8));
				inStream.skip((structCD[28] & 0xff) | ((structCD[29] & 0xff) << 8));

			}
		}

		// don't forget to put your file inside next time
		throw new IllegalArgumentException("JZip: " + path + ": entry not found");
	}
	
	public static boolean decodeData(byte[] out, byte[] in, int compressionMethod) {
		switch(compressionMethod) {
			// ZIP_CM_STORE
			case 0:
				System.arraycopy(in, 0, out, 0, out.length);
				return true;

			// ZIP_CM_DEFLATE
			case 8:
				return new Deflate(out, in).deflate();
		}

		return false;
	}

	public static boolean checkCompMethod(int comp) {
		for(int i = 0; i < SUPPORTED_COMPRESSION_METHODS.length; i++) {
			if(comp == SUPPORTED_COMPRESSION_METHODS[i]) return true;
		}

		return false;
	}
}
