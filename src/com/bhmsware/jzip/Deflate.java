package com.bhmsware.jzip;

public class Deflate {
	// tables
	public static final short LENGTH_TABLE[][] = {
		// length
		{3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258},
		// extra bits
		{0, 0, 0, 0, 0, 0, 0, 0,  1,  1,  1,  1,  2,  2,  2,  2,  3,  3,  3,  3,  4,  4,  4,  4,   5,   5,   5,   5,   0  }
	};
	public static final short DISTANCE_TABLE[][] = {
		// distance
		{1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577},
		// extra bits
		{0, 0, 0, 0, 1, 1, 2, 2,  3,  3,  4,  4,  5,  5,  6,   6,   7,   7,   8,   8,   9,    9,    10,   10,   11,   11,   12,   12,    13,    13   }
	};
	public static final byte CODE_LENGTH_ALPHABET[] = {
		16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
	};
	public static final short FIXED_CODES[];
	public static final byte FIXED_CODE_LENGTHS[];
	public static final short FIXED_DIST_CODES[];
	public static final byte FIXED_DIST_CODE_LENGTHS[];
	// buffers
	private byte[] out;
	private byte[] in;
	// frame
	private int frameIndex = 0;
	private int frame = 0x00000000;
	private int bit = 32;
	// indexes
	private int outIndex = 0;

	static {
		short fixedCodes[] = new short[288];
		byte fixedCodeLen[] = new byte[288];
		short fixedDistCodes[] = new short[30];
		byte fixedDistCodeLen[] = new byte[30];
		
		// fixed literal codes
		for(int i = 0; i < 144; i++)
			fixedCodeLen[i] = 8;
		for(int i = 144; i < 256; i++)
			fixedCodeLen[i] = 9;
		for(int i = 256; i < 280; i++)
			fixedCodeLen[i] = 7;
		for(int i = 280; i < 288; i++)
			fixedCodeLen[i] = 8;

		// fixed distance codes
		for(int i = 0; i < 30; i++) {
			fixedDistCodeLen[i] = 5;
		}

		// generate these codes
		generateCodes(fixedCodes, fixedCodeLen);
		generateCodes(fixedDistCodes, fixedDistCodeLen);

		// done
		FIXED_CODES = fixedCodes;
		FIXED_CODE_LENGTHS = fixedCodeLen;
		FIXED_DIST_CODES = fixedDistCodes;
		FIXED_DIST_CODE_LENGTHS = fixedDistCodeLen;
	}

	public Deflate(byte out[], byte in[]) {
		this.out = out;
		this.in = in;
	}

	private static void generateCodes(short codes[], byte codeLen[]) {
		short blCount[] = new short[16];
		short nextCode[] = new short[16];
		int maxBits = 0;

		// setting up blCount
		for(int i = 0; i < codeLen.length; i++) {
			blCount[codeLen[i]]++;
			if(maxBits < codeLen[i]) maxBits = codeLen[i];
		}
		blCount[0] = 0;

		// setting up nextCode
		for(int code = 0, bits = 1; bits <= maxBits; bits++) {
			code = (code + blCount[bits - 1]) << 1;
			nextCode[bits] = (short)code;
		}

		// generate the tree itself
		for(int i = 0; i < codes.length; i++) {
			if(codeLen[i] != 0) {
				codes[i] = nextCode[codeLen[i]]++;
			}
		}
	}

	private int decodeNextValue(short codes[], byte codeLen[], byte tempCodeLen[]) {
		System.arraycopy(codeLen, 0, tempCodeLen, 0, tempCodeLen.length);

		int bitIndex = 0;
		while(true) {
			int bitRead = -1;

			for(int i = 0; i < tempCodeLen.length; i++) {

				if(tempCodeLen[i] > 0) {

					int shift = tempCodeLen[i] - 1 - bitIndex;
					if(shift < 0) {
						return i;
					}

					if(bitRead < 0) bitRead = readBit();
					if(((codes[i] >> shift) & 1) != bitRead) {
						tempCodeLen[i] = 0;
					}

				}
			}

			bitIndex++;
		}
	}

	public boolean deflate() {
		// header of blocks
		boolean BFINAL;
		int BTYPE;

		// decode cycle for each block
		do {
			// huffman related
			short codes[] = null;
			byte codeLen[] = null;
			short distCodes[] = null;
			byte distCodeLen[] = null;

			// header of this block
			BFINAL = readBit() == 1;
			BTYPE = readConst(2);

			switch(BTYPE) {
				// raw uncompressed
				case 0: {
					int i = frameIndex - 3;

					int LEN = (in[i] & 0xff) | ((in[i + 1] & 0xff) << 8);
					// int NLEN = (in[i + 2] & 0xff) | ((in[i + 3] & 0xff) << 8);
					System.arraycopy(in, i + 4, out, outIndex, LEN);

					outIndex += LEN;
					frameIndex += LEN + 1;
					bit = 32;

					break;
				}

				// fixed huffman
				case 1: {
					codes = FIXED_CODES;
					codeLen = FIXED_CODE_LENGTHS;
					distCodes = FIXED_DIST_CODES;
					distCodeLen = FIXED_DIST_CODE_LENGTHS;

					break;
				}

				// dynamic huffman
				case 2: {
					// another header ig
					int HLIT = 257 + readConst(5);
					int HDIST = 1 + readConst(5);
					int HCLEN = 4 + readConst(4);

					byte codeLen4cl[] = new byte[19];
					short codes4cl[] = new short[19];

					for(int i = 0; i < HCLEN; i++) {
						codeLen4cl[CODE_LENGTH_ALPHABET[i]] = (byte)readConst(3);
					}

					// generate codes for code lengths
					generateCodes(codes4cl, codeLen4cl);

					codes = new short[288];
					codeLen = new byte[288];
					distCodes = new short[30];
					distCodeLen = new byte[30];

					short currentCodes[] = codes;
					byte currentCodeLen[] = codeLen;
					int count = HLIT;

					// decode code lengths => setting up literal code lengths, then distance code lengths
					byte tempCodeLen4cl[] = new byte[19];
					for(int p = 0; p < 2; p++) {

						if(p > 0) {
							currentCodes = distCodes;
							currentCodeLen = distCodeLen;
							count = HDIST;
						}

						for(int i = 0; i < count;) {
							int value = decodeNextValue(codes4cl, codeLen4cl, tempCodeLen4cl);

							switch(value) {
								case 16:
									int target = i + (3 + readConst(2));
									int prev = currentCodeLen[i - 1];
									for(; i < target; i++)
										currentCodeLen[i] = (byte)prev;
									break;

								case 17:
									i += 3 + readConst(3);
									break;

								case 18:
									i += 11 + readConst(7);
									break;

								default:
									currentCodeLen[i++] = (byte)value;
							}
						}

					}

					// generate these codes
					generateCodes(codes, codeLen);
					generateCodes(distCodes, distCodeLen);

					break;
				}

				// reserved
				default:
					return false;
			}

			if(BTYPE != 0) {
				// value decoding
				byte tempCodeLen[] = new byte[288];
				byte tempDistCodeLen[] = new byte[30];
				int value;
				while((value = decodeNextValue(codes, codeLen, tempCodeLen)) != 256) {
					if(value < 256) {

						// literal byte
						out[outIndex++] = (byte)value;

					} else if(value < 286) {

						// length-distance pair

						// target (got from length)
						int target = outIndex + (LENGTH_TABLE[0][value - 257] + readConst(LENGTH_TABLE[1][value - 257]));
						// distance
						int dist = decodeNextValue(distCodes, distCodeLen, tempDistCodeLen);
						dist = DISTANCE_TABLE[0][dist] + readConst(DISTANCE_TABLE[1][dist]);

						// copy
						for(; outIndex < target; outIndex++)
							out[outIndex] = out[outIndex - dist];

					} else if(value >= 286) {

						// reserved
						return false;

					}
				}
			}

		} while(!BFINAL);

		return true;
	}

	private int readConst(int len) {
		shiftFrame();
		int i = (frame >> bit) & ((1 << len) - 1);
		bit += len;
		return i;
	}

	private int readBit() {
		shiftFrame();
		return ((frame >> bit++) & 1) & 1;
	}

	private void shiftFrame() {
		while(bit >= 8) {
			frame >>= 8;
			frame &= 0xffffff;
			bit -= 8;
			if(frameIndex < in.length) frame |= (in[frameIndex++] << 24);
		}
	}
}
