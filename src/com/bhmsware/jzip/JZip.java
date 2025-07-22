package com.bhmsware.jzip;

import java.io.*;

public final class JZip {
	private static final int MAX_VERSION_SUPPORTED = 20;
	private static final int[] SUPPORTED_COMPRESSION_METHODS = {
		0, 8
	};

	private JZip() {
	}

	public static byte[] extractFile(InputStream is, String path) throws Exception {
		DataInputStream data = new DataInputStream(is);

		int versionToExtract,
			compressionMethod,
			filenameLength,
			extraFieldLength,
			fileCommentLength;
		byte compressed[];
		byte uncompressed[];

		try {
			while(true) {
				int signature = data.readInt();
				switch(signature) {
					// Central Directory header
					case 0x504b0102:
						// version made (1 bytes)
						// OS made (1 bytes)
						// version to extract (1 bytes)
						// OS to extract (1 bytes)
						// bit flag (2 bytes)
						// compression method (2 bytes)
						// mod time (2 bytes)
						// mod date (2 bytes)
						// CRC-32 (4 bytes)
						// compressed size (4 bytes)
						// uncompressed size (4 bytes)
						data.skip(24);
						// filename length
						filenameLength = data.readUnsignedByte() | (data.readUnsignedByte() << 8);
						// extra field length
						extraFieldLength = data.readUnsignedByte() | (data.readUnsignedByte() << 8);
						// comment length
						fileCommentLength = data.readUnsignedByte() | (data.readUnsignedByte() << 8);
						// disk num (2 bytes)
						// int. file attributes (2 bytes)
						// ext. file attributes (4 bytes)
						// Local File Header offset (4 bytes)
						// filename
						// extra field data
						// file comment
						data.skip(12 + filenameLength + extraFieldLength + fileCommentLength);

						break;
		
					// Local File Header
					case 0x504b0304:
						// version to extract
						versionToExtract = data.readUnsignedByte();
						if(versionToExtract > MAX_VERSION_SUPPORTED) {
							throw new IllegalArgumentException("ZIP: extract " + path + " failed: " + "maximum version " + MAX_VERSION_SUPPORTED + " is only supported, but " + versionToExtract + " found");
						}
						// OS to extract (1 byte)
						// bit flag (2 bytes)
						data.skip(3);
						// compression method
						compressionMethod = data.readUnsignedByte() | (data.readUnsignedByte() << 8);
						if(!checkCompMethod(compressionMethod)) {
							throw new IllegalArgumentException("ZIP: extract " + path + " failed: " + "unsupported compression method " + compressionMethod);
						}
						// mod time (2 bytes)
						// mod date (2 bytes)
						// CRC-32 (4 bytes)
						data.skip(8);
						// compressed size
						compressed = new byte[data.readUnsignedByte() | (data.readUnsignedByte() << 8) | (data.readUnsignedByte() << 16) | (data.readUnsignedByte() << 24)];
						// uncompressed size
						uncompressed = new byte[data.readUnsignedByte() | (data.readUnsignedByte() << 8) | (data.readUnsignedByte() << 16) | (data.readUnsignedByte() << 24)];
						// filename length
						filenameLength = data.readUnsignedByte() | (data.readUnsignedByte() << 8);
						// extra field length
						extraFieldLength = data.readUnsignedByte() | (data.readUnsignedByte() << 8);
						// filename
						byte[] filename = new byte[filenameLength];
						data.read(filename);
						// extra field
						data.skip(extraFieldLength);
						// data decoding
						data.read(compressed);
						if(path.equals(new String(filename))) {
							if(!decodeData(uncompressed, compressed, compressionMethod)) {
								throw new IllegalArgumentException("ZIP: extract " + path + " failed: " + "invalid compressed data to inflate");
							}
							return uncompressed;
						}
		
						break;

					// End Of Central Directory Record
					case 0x504b0506:
						// disk num (2 bytes)
						// start disk num (2 bytes)
						// CD num in disk (2 bytes)
						// total CD num (2 bytes)
						// CD size (4 bytes)
						// CD offset (4 bytes)
						// comment length (2 bytes)
						// comment
						data.skip(16 + (data.readUnsignedByte() | (data.readUnsignedByte() << 8)));

						break;

					default:
						throw new IllegalArgumentException("ZIP: extract " + path + " failed: " + "invalid signature " + Integer.toHexString(signature));
				}
			}
		} catch(EOFException e) {
			throw new EOFException("ZIP: extract " + path + " failed: " + "file not found");
		}
	}
	
	private static boolean decodeData(byte[] out, byte[] in, int compressionMethod) {
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

	private static boolean checkCompMethod(int comp) {
		for(int i = 0; i < SUPPORTED_COMPRESSION_METHODS.length; i++) {
			if(comp == SUPPORTED_COMPRESSION_METHODS[i]) return true;
		}

		return false;
	}
}
