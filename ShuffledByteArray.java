/*
 * Copyright (c) 2017, DB Systel GmbH
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Author: Frank Schwab, DB Systel GmbH
 *
 * Changes: 
 *     2015-09-26: V1.0.0: Created
 */
package dbscryptolib;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Stores a byte array in a shuffled form.
 *
 * @author Frank Schwab
 * @version 1.0.0
 */
public final class ShuffledByteArray implements AutoCloseable {

   private byte[] byteArray;

   private int[] indexArray;
   private int indexOffset;
   private int indexFactor;
   private int indexStart;

   private int realLength;

   private int hashCode;
   
   private boolean isValid;

   private final SecureRandom sprng = new SecureRandom();

   /**
    * Constructor for the shuffled byte array with a source array
    *
    * @param sourceArray Source byte array
    * @throws IllegalArgumentException
    */
   public ShuffledByteArray(byte[] sourceArray) throws IllegalArgumentException {
      checkArray(sourceArray);
      
      this.initializeDataStructures(sourceArray.length);
      this.setValues(sourceArray);

      this.hashCode = Arrays.hashCode(sourceArray);   // Calculate hash code of source only once
              
      this.isValid = true;
   }

   /*
    * Private methods
    */

   /*
    * Check methods
    */
   
   /**
    * Checks whether array is valid
    * <p>
    * Note: An array length of 0 is allowed.
    * </p>
    *
    * @param sourceArray A byte array
    * @throws IllegalArgumentException if <code>sourceArray</code> is null
    */
   private void checkArray(byte[] sourceArray) throws IllegalArgumentException {
      if (sourceArray == null) {
         throw new IllegalArgumentException("sourceArray is null");
      }
   }
   /**
    * Checks whether the shuffled byte array is in a valid state
    *
    * @throws IllegalStateException if the shuffled array has already been
    * destroyed
    */
   private void checkState() throws IllegalStateException {
      if (!this.isValid) {
         throw new IllegalStateException("ShuffledByteArray has already been destroyed");
      }
   }

   /**
    * Checks whether a given external index is valid
    * 
    * @param externalIndex Index value to be checked
    * @throws ArrayIndexOutOfBoundsException 
    */
   private void checkExternalIndex(int externalIndex) throws ArrayIndexOutOfBoundsException {
      if ((externalIndex < 0) || (externalIndex >= this.realLength)) {
            throw new ArrayIndexOutOfBoundsException("Illegal index " + Integer.toString(externalIndex));
      }
   }
   
   /**
    * Checks the state and then the validity of the given external index
    * 
    * @param externalIndex Index value to be checked
    */
   private void checkStateAndExternalIndex(int externalIndex) {
      checkState();
      checkExternalIndex(externalIndex);
   }

   /*
    * Methods for data structure initialization and maitenance
    */

   /**
    * Calculates the array size required for storing the data.
    * The stored array has at least twice the size of the original array to be
    * able to set a random start point in the index reorder array.
    *
    * @param forSize Original size
    * @return Size of shuffled array
    */
   private int getStoreLength(int forSize) {
      final int calcSize = forSize + forSize + 7;
 
      return this.sprng.nextInt(calcSize) + calcSize;
   }
   
   /**
    * Gets the offset for index obfuscation
    *
    * @param arrayLength Length of the array
    * @return Offset for indices
    */
   private int getIndexOffset(int arrayLength) {
      return this.sprng.nextInt(100000) + arrayLength + arrayLength + 1;
   }

   /**
    * Gets the factor for index obfuscation
    *
    * @param offset Offset for indices
    * @param arrayLength Length of the array
    * @return Factor for indices
    */
   private int getIndexFactor(int offset, int arrayLength) {
      return (5 * offset) / (3 * arrayLength);
   }
   /**
    * Initializes the index array with each position holding it's own index in
    * store index form.
    */
   private void initializeIndexArray() {
      for (int i = 0; i < this.indexArray.length; i++) {
         this.indexArray[i] = getStoreIndex(i);
      }
   }

   /**
    * Shuffles the positions in the index array.
    */
   private void shuffleIndexArray() {
      int i1;
      int i2;
      int swap;

      int count = 0;

      final int arrayLength = this.indexArray.length;

      do {
         i1 = this.sprng.nextInt(arrayLength);
         i2 = this.sprng.nextInt(arrayLength);

         // Swapping is inlined for performance
         if (i1 != i2) {
            swap = this.indexArray[i1];
            this.indexArray[i1] = this.indexArray[i2];
            this.indexArray[i2] = swap;

            count++;
         }
      } while (count < arrayLength);
   }

   /**
    * Gets the start position in an array
    *
    * @param arrayLength Length of the array to get the start position for
    * @return Start position in the array
    */
   private int getStartPosition(int arrayLength) {
      // "+1" because the max. start position is at the half size of
      // the array.
      return this.sprng.nextInt((arrayLength >> 1) + 1);
   }

   /**
    * Reorganizes the index array for reordering of the byte array.
    *
    * This includes setting a random start position in the index array.
    */
   private void reorganizeIndexArray() {
      shuffleIndexArray();

      this.indexStart = getStartPosition(this.indexArray.length);
   }

   /**
    * Sets up the index array by initializing and shuffling it
    */
   private void setUpIndexArray() {
      initializeIndexArray();
      reorganizeIndexArray();
   }

   /**
    * Allocates and initializes all necessary arrays
    *
    * @param sourceLength Length of source array
    */
   private void initializeDataStructures(int sourceLength) {
      this.realLength = sourceLength;

      final int storeLength = getStoreLength(this.realLength);

      this.byteArray = new byte[storeLength];
      this.sprng.nextBytes(this.byteArray);   // Initialize the data with random values
      
      this.indexArray = new int[storeLength];

      this.indexOffset = getIndexOffset(storeLength);
      this.indexFactor = getIndexFactor(this.indexOffset, storeLength);
      setUpIndexArray();
   }

   /**
    * Clears all data
    */
   private void clearData() {
      Arrays.fill(this.byteArray, (byte) 0); // Clear sensitive data

      Arrays.fill(this.indexArray, (int) 0);
      
      this.indexStart = 0;
      this.indexOffset = 0;
      this.indexFactor = 0;

      this.hashCode = 0;
      
      this.realLength = 0;
   }

   /**
    * Gets the store index from the real index
    *
    * @param realIndex Real index
    * @return Store index
    */
   private int getStoreIndex(int realIndex) {
      // 2 is added so that the indexOffset is not put out for a realIndex of 0
      // and neither is the difference of indexOffset and indexFactor.
      return (this.indexFactor * (realIndex + 2)) - this.indexOffset;
   }

   /**
    * Gets the real index from the store index
    *
    * @param storeIndex Store index
    * @return Real index
    */
   private int getRealIndex(int storeIndex) {
      return ((storeIndex + this.indexOffset) / this.indexFactor) - 2;
   }

   /**
    * Gets the array index from the external index
    *
    * @param externalIndex External index
    * @return The index into the byte array
    */
   private int getArrayIndex(int externalIndex) {
      return getRealIndex(this.indexArray[externalIndex + this.indexStart]);
   }

   /*
    * Methods for accessing data from or to byte array
    */
 
   /**
    * Sets the destination array to the values in the source array.
    *
    * @param sourceArray Source byte array
    */
   private void setValues(byte[] sourceArray) {
      for (int i = 0; i < sourceArray.length; i++) {
         this.byteArray[getArrayIndex(i)] = sourceArray[i];
      }
   }

   /**
    * Gets the values from the shuffled array.
    *
    * @return Values stored in shuffled byte array
    */
   private byte[] getValues() {
      final byte[] result = new byte[this.realLength];

      for (int i = 0; i < result.length; i++) {
         result[i] = this.byteArray[getArrayIndex(i)];
      }

      return result;
   }
   
   /*
    * Public methods
    */

   /*
    * Access methods
    */

   /**
    * Gets the original array content
    *
    * @return Original array content
    * @throws IllegalStateException if the shuffled array has already been
    * destroyed
    */
   public byte[] getData() throws IllegalStateException {
      checkState();

      return getValues();
   }

   /**
    * Gets an array element at a given position
    * 
    * @param externalIndex Index of the array element
    * @return Value of the array element at the given position
    * @throws IllegalStateException
    * @throws ArrayIndexOutOfBoundsException 
    */
   public byte getAt(int externalIndex) throws IllegalStateException, ArrayIndexOutOfBoundsException {
      checkStateAndExternalIndex(externalIndex);

      return this.byteArray[getArrayIndex(externalIndex)];
   }

   /**
    * Sets the array element at a given position to a given value
    * 
    * @param externalIndex Index of the array element
    * @param newValue New value of the array element
    * @throws IllegalStateException
    * @throws ArrayIndexOutOfBoundsException 
    */
   public void setAt(int externalIndex, byte newValue) throws IllegalStateException, ArrayIndexOutOfBoundsException {
      checkStateAndExternalIndex(externalIndex);

      this.byteArray[getArrayIndex(externalIndex)] = newValue;
   }
   
   /**
    * Gets the real array length
    *
    * @return Real length of stored array
    * @throws IllegalStateException if the shuffled array has already been
    * destroyed
    */
   public int length() throws IllegalStateException {
      checkState();

      return this.realLength;
   }

   /**
    * Checks whether this ShuffledByteArray is valid
    * 
    * @return <code>True</code>, if this ShuffledByteArray is valid. <code>False</code>, if it has been deleted
    */
   public boolean isValid() {
      return this.isValid;
   }

   /**
    * Returns the hash code of this <code>ShuffledByteArray</code> instance.
    *
    * @return The hash code.
    * @throws IllegalStateException if this shuffled byte array has already been
    * destroyed.
    */
   @Override
   public int hashCode() throws IllegalStateException {
      checkState();

      return this.hashCode;
   }

   /**
    * Compares the specified object with this <code>ShuffledByteArray</code>
    * instance.
    *
    * @param obj The object to compare.
    * @return true if byte arrays of both object are equal, otherwise false.
    * @throws IllegalStateException if the protected array has already been
    * destroyed.
    */
   @Override
   public boolean equals(Object obj) throws IllegalStateException {
      if (obj == null) {
         return false;
      }

      if (getClass() != obj.getClass()) {
         return false;
      }

      final ShuffledByteArray other = (ShuffledByteArray) obj;
      final byte[] thisClearArray = this.getData();
      final byte[] otherClearArray = other.getData();
      boolean result = Arrays.equals(thisClearArray, otherClearArray);

      Arrays.fill(thisClearArray, (byte) 0); // Clear sensitive data
      Arrays.fill(otherClearArray, (byte) 0); // Clear sensitive data

      return result;
   }
   
   /*
    * Method for AutoCloseable interface
    */
   /**
    * Secure deletion of shuffled array.
    *
    * This method is idempotent and never throws an exception.
    */
   @Override
   public void close() {
      if (this.isValid) {
         this.isValid = false;

         clearData();
      }
   }
}
