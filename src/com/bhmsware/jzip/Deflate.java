package com.bhmsware.jzip;

public class Deflate {
	// tables
	public static final int LENGTH_TABLE[][] = {
		// length
		{3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258},
		// extra bits
		{0, 0, 0, 0, 0, 0, 0, 0,  1,  1,  1,  1,  2,  2,  2,  2,  3,  3,  3,  3,  4,  4,  4,  4,   5,   5,   5,   5,   0  }
	};
	public static final int DISTANCE_TABLE[][] = {
		// distance
		{1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577},
		// extra bits
		{0, 0, 0, 0, 1, 1, 2, 2,  3,  3,  4,  4,  5,  5,  6,   6,   7,   7,   8,   8,   9,    9,    10,   10,   11,   11,   12,   12,    13,    13   }
	};
	public static final int CODE_LENGTH_ALPHABET[] = {
		16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15
	};
	public static final int FIXED_CODES[];
	public static final int FIXED_CODE_LENGTHS[];
	public static final int FIXED_DIST_CODES[];
	public static final int FIXED_DIST_CODE_LENGTHS[];
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
		int[] fixedCodes = new int[288];
		int[] fixedCodeLen = new int[288];
		int[] fixedDistCodes = new int[30];
		int[] fixedDistCodeLen = new int[30];
		
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

	public Deflate(byte[] out, byte[] in) {
		this.out = out;
		this.in = in;
	}

	private static void generateCodes(int codes[], int codeLen[]) {
		int blCount[] = new int[16];
		int nextCode[] = new int[16];
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
			nextCode[bits] = code;
		}

		// generate the tree itself
		for(int i = 0; i < codes.length; i++) {
			if(codeLen[i] != 0) {
				codes[i] = nextCode[codeLen[i]]++;
			}
		}
	}

	private int decodeNextValue(int codes[], int codeLen[]) {
		int workingCodeLen[] = new int[codeLen.length];
		System.arraycopy(codeLen, 0, workingCodeLen, 0, workingCodeLen.length);

		int bitIndex = 0;
		while(true) {
			int bitRead = -1;

			for(int i = 0; i < workingCodeLen.length; i++) {

				if(workingCodeLen[i] > 0) {

					int shift = workingCodeLen[i] - 1 - bitIndex;
					if(shift < 0) {
						return i;
					}

					if(bitRead < 0) bitRead = readBit();
					if(((codes[i] >> shift) & 1) != bitRead) {
						workingCodeLen[i] = 0;
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
			int codes[] = null;
			int codeLen[] = null;
			int distCodes[] = null;
			int distCodeLen[] = null;

			// header of this block
			BFINAL = readBit() == 1;
			BTYPE = readConst(2);

			switch(BTYPE) {
				// raw uncompressed
				case 0: {
					int i = frameIndex - 3;

					int LEN = (in[i] & 0xff) | ((in[i + 1] & 0xff) << 8);
					System.out.println(LEN);
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

					int codeLen4cl[] = new int[19];
					int codes4cl[] = new int[19];

					for(int i = 0; i < HCLEN; i++) {
						codeLen4cl[CODE_LENGTH_ALPHABET[i]] = readConst(3);
					}

					// generate codes for code lengths
					generateCodes(codes4cl, codeLen4cl);

					codes = new int[288];
					codeLen = new int[288];
					distCodes = new int[30];
					distCodeLen = new int[30];

					int currentCodes[] = codes;
					int currentCodeLen[] = codeLen;
					int count = HLIT;

					// decode code lengths => setting up literal code lengths, then distance code lengths
					for(int p = 0; p < 2; p++) {

						if(p > 0) {
							currentCodes = distCodes;
							currentCodeLen = distCodeLen;
							count = HDIST;
						}

						for(int i = 0; i < count;) {
							int value = decodeNextValue(codes4cl, codeLen4cl);

							switch(value) {
								case 16:
									int target = i + (3 + readConst(2));
									int prev = currentCodeLen[i - 1];
									for(; i < target; i++)
										currentCodeLen[i] = prev;
									break;

								case 17:
									i += 3 + readConst(3);
									break;

								case 18:
									i += 11 + readConst(7);
									break;

								default:
									currentCodeLen[i++] = value;
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
				int value;
				while((value = decodeNextValue(codes, codeLen)) != 256) {
					if(value < 256) {

						// literal byte
						out[outIndex++] = (byte)value;

					} else if(value < 286) {

						// length-distance pair

						// target (got from length)
						int target = outIndex + (LENGTH_TABLE[0][value - 257] + readConst(LENGTH_TABLE[1][value - 257]));
						// distance
						int dist = decodeNextValue(distCodes, distCodeLen);
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
