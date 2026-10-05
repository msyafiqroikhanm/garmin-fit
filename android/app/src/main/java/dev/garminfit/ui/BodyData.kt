package dev.garminfit.ui

// Body outline polygons ported from react-body-highlighter 2.0.5 (src/assets/index.ts), viewBox 100×200.
/*
 * MIT License
 *
 * Copyright (c) 2020 GV79
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/** Region name from the source data, then flat x,y pairs. */
class BodyPolygon(val region: String, val points: FloatArray)

val BODY_FRONT = listOf(
    BodyPolygon("CHEST", floatArrayOf(51.8f, 41.6f, 51.0f, 55.1f, 58.0f, 58.0f, 67.8f, 55.5f, 70.6f, 47.3f, 62.0f, 41.6f)),
    BodyPolygon("CHEST", floatArrayOf(29.8f, 46.5f, 31.4f, 55.5f, 40.8f, 58.0f, 48.2f, 55.1f, 47.8f, 42.0f, 37.6f, 42.0f)),
    BodyPolygon("OBLIQUES", floatArrayOf(68.6f, 63.3f, 67.3f, 57.1f, 58.8f, 59.6f, 60.0f, 64.1f, 60.4f, 83.3f, 65.7f, 78.8f, 66.5f, 69.8f)),
    BodyPolygon("OBLIQUES", floatArrayOf(33.9f, 78.4f, 33.1f, 71.8f, 31.0f, 63.3f, 32.2f, 57.1f, 40.8f, 59.2f, 39.2f, 63.3f, 39.2f, 83.7f)),
    BodyPolygon("ABS", floatArrayOf(56.3f, 59.2f, 58.0f, 64.1f, 58.4f, 78.0f, 58.4f, 92.7f, 56.3f, 98.4f, 55.1f, 104.1f, 51.4f, 107.8f, 51.0f, 84.5f, 50.6f, 67.3f, 51.0f, 57.1f)),
    BodyPolygon("ABS", floatArrayOf(43.7f, 58.8f, 48.6f, 57.1f, 49.0f, 67.3f, 48.6f, 84.5f, 48.2f, 107.3f, 44.5f, 103.7f, 40.8f, 91.4f, 40.8f, 78.4f, 41.2f, 64.5f)),
    BodyPolygon("BICEPS", floatArrayOf(16.7f, 68.2f, 18.0f, 71.4f, 22.9f, 66.1f, 29.0f, 53.9f, 27.8f, 49.4f, 20.4f, 55.9f)),
    BodyPolygon("BICEPS", floatArrayOf(71.4f, 49.4f, 70.2f, 54.7f, 76.3f, 66.1f, 81.6f, 71.8f, 82.9f, 69.0f, 78.8f, 55.5f)),
    BodyPolygon("TRICEPS", floatArrayOf(69.4f, 55.5f, 69.4f, 61.6f, 75.9f, 72.7f, 77.6f, 70.2f, 75.5f, 67.3f)),
    BodyPolygon("TRICEPS", floatArrayOf(22.4f, 69.4f, 29.8f, 55.5f, 29.8f, 60.8f, 22.9f, 73.1f)),
    BodyPolygon("NECK", floatArrayOf(55.5f, 23.7f, 50.6f, 33.5f, 50.6f, 39.2f, 61.6f, 40.0f, 70.6f, 44.9f, 69.4f, 36.7f, 63.3f, 35.1f, 58.4f, 30.6f)),
    BodyPolygon("NECK", floatArrayOf(29.0f, 44.9f, 30.2f, 37.1f, 36.3f, 35.1f, 41.2f, 30.2f, 44.5f, 24.5f, 49.0f, 33.9f, 48.6f, 39.2f, 38.0f, 39.6f)),
    BodyPolygon("FRONT_DELTOIDS", floatArrayOf(78.4f, 53.1f, 79.6f, 47.8f, 79.2f, 41.2f, 75.9f, 38.0f, 71.0f, 36.3f, 72.2f, 42.9f, 71.4f, 47.3f)),
    BodyPolygon("FRONT_DELTOIDS", floatArrayOf(28.2f, 47.3f, 21.2f, 53.1f, 20.0f, 47.8f, 20.4f, 40.8f, 24.5f, 37.1f, 28.6f, 37.1f, 26.9f, 43.3f)),
    BodyPolygon("HEAD", floatArrayOf(42.4f, 2.9f, 40.0f, 11.8f, 42.0f, 19.6f, 46.1f, 23.3f, 49.8f, 25.3f, 54.7f, 22.4f, 57.6f, 19.2f, 59.2f, 10.2f, 57.1f, 2.4f, 49.8f, 0.0f)),
    BodyPolygon("ABDUCTORS", floatArrayOf(52.7f, 110.2f, 54.3f, 124.9f, 60.0f, 110.2f, 62.0f, 100.0f, 64.9f, 94.3f, 60.0f, 92.7f, 56.7f, 104.5f)),
    BodyPolygon("ABDUCTORS", floatArrayOf(47.8f, 110.6f, 44.9f, 125.3f, 42.0f, 115.9f, 40.4f, 113.1f, 39.6f, 107.3f, 38.0f, 102.4f, 34.7f, 93.9f, 39.6f, 92.2f, 41.6f, 99.2f, 43.7f, 105.3f)),
    BodyPolygon("QUADRICEPS", floatArrayOf(34.7f, 98.8f, 37.1f, 108.2f, 37.1f, 127.8f, 34.3f, 137.1f, 31.0f, 132.7f, 29.4f, 120.0f, 28.2f, 111.4f, 29.4f, 100.8f, 32.2f, 94.7f)),
    BodyPolygon("QUADRICEPS", floatArrayOf(63.3f, 105.7f, 64.5f, 100.0f, 66.9f, 94.7f, 70.2f, 101.2f, 71.0f, 111.8f, 68.2f, 133.1f, 65.3f, 137.6f, 62.4f, 128.6f, 62.0f, 111.4f)),
    BodyPolygon("QUADRICEPS", floatArrayOf(38.8f, 129.4f, 38.4f, 112.2f, 41.2f, 118.4f, 44.5f, 129.4f, 42.9f, 135.1f, 40.0f, 146.1f, 36.3f, 146.5f, 35.5f, 140.0f)),
    BodyPolygon("QUADRICEPS", floatArrayOf(59.6f, 145.7f, 55.5f, 129.0f, 60.8f, 113.9f, 61.2f, 130.2f, 64.1f, 139.6f, 62.9f, 146.5f)),
    BodyPolygon("QUADRICEPS", floatArrayOf(32.7f, 138.4f, 26.5f, 145.7f, 25.7f, 136.7f, 25.7f, 127.3f, 26.9f, 114.3f, 29.4f, 133.5f)),
    BodyPolygon("QUADRICEPS", floatArrayOf(71.8f, 113.1f, 73.9f, 124.1f, 73.9f, 140.4f, 72.7f, 145.7f, 66.5f, 138.4f, 70.2f, 133.5f)),
    BodyPolygon("KNEES", floatArrayOf(33.9f, 140.0f, 34.7f, 143.3f, 35.5f, 147.3f, 36.3f, 151.0f, 35.1f, 156.7f, 29.8f, 156.7f, 27.3f, 152.7f, 27.3f, 147.3f, 30.2f, 144.1f)),
    BodyPolygon("KNEES", floatArrayOf(65.7f, 140.0f, 72.2f, 147.8f, 72.2f, 152.2f, 69.8f, 157.1f, 64.9f, 156.7f, 62.9f, 151.0f)),
    BodyPolygon("CALVES", floatArrayOf(71.4f, 160.4f, 73.5f, 153.5f, 76.7f, 161.2f, 79.6f, 167.8f, 78.4f, 187.8f, 79.6f, 195.5f, 74.7f, 195.5f)),
    BodyPolygon("CALVES", floatArrayOf(24.9f, 194.7f, 27.8f, 164.9f, 28.2f, 160.4f, 26.1f, 154.3f, 24.9f, 157.6f, 22.4f, 161.6f, 20.8f, 167.8f, 22.0f, 188.2f, 20.8f, 195.5f)),
    BodyPolygon("CALVES", floatArrayOf(72.7f, 195.1f, 69.8f, 159.2f, 65.3f, 158.4f, 64.1f, 162.4f, 64.1f, 165.3f, 65.7f, 177.1f)),
    BodyPolygon("CALVES", floatArrayOf(35.5f, 158.4f, 35.9f, 162.4f, 35.9f, 166.9f, 35.1f, 172.2f, 35.1f, 176.7f, 32.2f, 182.0f, 30.6f, 187.3f, 26.9f, 194.7f, 27.3f, 187.8f, 28.2f, 180.4f, 28.6f, 175.5f, 29.0f, 169.8f, 29.8f, 164.1f, 30.2f, 158.8f)),
    BodyPolygon("FOREARM", floatArrayOf(6.1f, 88.6f, 10.2f, 75.1f, 14.7f, 70.2f, 16.3f, 74.3f, 19.2f, 73.5f, 4.5f, 97.6f, 0.0f, 100.0f)),
    BodyPolygon("FOREARM", floatArrayOf(84.5f, 69.8f, 83.3f, 73.5f, 80.0f, 73.1f, 95.1f, 98.4f, 100.0f, 100.4f, 93.5f, 89.4f, 89.8f, 76.3f)),
    BodyPolygon("FOREARM", floatArrayOf(77.6f, 72.2f, 77.6f, 77.6f, 80.4f, 84.1f, 85.3f, 89.8f, 92.2f, 101.2f, 94.7f, 99.6f)),
    BodyPolygon("FOREARM", floatArrayOf(6.9f, 101.2f, 13.5f, 90.6f, 18.8f, 84.1f, 21.6f, 77.1f, 21.2f, 71.8f, 4.9f, 98.8f)),
)

val BODY_BACK = listOf(
    BodyPolygon("HEAD", floatArrayOf(50.6f, 0.0f, 46.0f, 0.9f, 40.9f, 5.5f, 40.4f, 12.8f, 45.1f, 20.0f, 55.7f, 20.0f, 59.1f, 13.6f, 59.6f, 4.7f, 55.7f, 1.3f)),
    BodyPolygon("TRAPEZIUS", floatArrayOf(44.7f, 21.7f, 47.7f, 21.7f, 47.2f, 38.3f, 47.7f, 64.7f, 38.3f, 53.2f, 35.3f, 40.9f, 31.1f, 36.6f, 39.1f, 33.2f, 43.8f, 27.2f)),
    BodyPolygon("TRAPEZIUS", floatArrayOf(52.3f, 21.7f, 55.7f, 21.7f, 56.6f, 27.2f, 60.9f, 32.8f, 68.9f, 36.6f, 64.7f, 40.4f, 61.7f, 53.2f, 52.3f, 64.7f, 53.2f, 38.3f)),
    BodyPolygon("BACK_DELTOIDS", floatArrayOf(29.4f, 37.0f, 23.0f, 39.1f, 17.4f, 44.3f, 18.3f, 53.6f, 24.3f, 49.4f, 27.2f, 46.4f)),
    BodyPolygon("BACK_DELTOIDS", floatArrayOf(71.1f, 37.0f, 78.3f, 39.6f, 82.6f, 44.7f, 81.7f, 53.6f, 74.9f, 48.9f, 72.3f, 45.1f)),
    BodyPolygon("UPPER_BACK", floatArrayOf(31.1f, 38.7f, 28.1f, 48.9f, 28.5f, 55.3f, 34.0f, 75.3f, 47.2f, 71.1f, 47.2f, 66.4f, 36.6f, 54.0f, 33.6f, 41.3f)),
    BodyPolygon("UPPER_BACK", floatArrayOf(68.9f, 38.7f, 71.9f, 49.4f, 71.5f, 56.2f, 66.0f, 75.3f, 52.8f, 71.1f, 52.8f, 66.4f, 63.4f, 54.5f, 66.4f, 41.7f)),
    BodyPolygon("TRICEPS", floatArrayOf(26.8f, 49.8f, 17.9f, 55.7f, 14.5f, 72.3f, 16.6f, 81.7f, 21.7f, 63.8f, 26.8f, 55.7f)),
    BodyPolygon("TRICEPS", floatArrayOf(73.6f, 50.2f, 82.1f, 55.7f, 86.0f, 73.2f, 83.4f, 82.1f, 77.9f, 63.0f, 73.2f, 55.7f)),
    BodyPolygon("TRICEPS", floatArrayOf(26.8f, 58.3f, 26.8f, 68.5f, 23.0f, 75.3f, 19.1f, 77.4f, 22.6f, 65.5f)),
    BodyPolygon("TRICEPS", floatArrayOf(72.8f, 58.3f, 77.0f, 64.7f, 80.4f, 77.4f, 76.6f, 75.3f, 72.8f, 68.9f)),
    BodyPolygon("LOWER_BACK", floatArrayOf(47.7f, 72.8f, 34.5f, 77.0f, 35.3f, 83.4f, 49.4f, 102.1f, 46.8f, 83.0f)),
    BodyPolygon("LOWER_BACK", floatArrayOf(52.3f, 72.8f, 65.5f, 77.0f, 64.7f, 83.4f, 50.6f, 102.1f, 53.2f, 83.8f)),
    BodyPolygon("FOREARM", floatArrayOf(86.4f, 75.7f, 91.1f, 83.4f, 93.2f, 94.0f, 100.0f, 106.4f, 96.2f, 104.3f, 88.1f, 89.4f, 84.3f, 83.8f)),
    BodyPolygon("FOREARM", floatArrayOf(13.6f, 75.7f, 8.9f, 83.8f, 6.8f, 93.6f, 0.0f, 106.4f, 3.8f, 104.3f, 12.3f, 88.5f, 15.7f, 83.0f)),
    BodyPolygon("FOREARM", floatArrayOf(81.3f, 79.6f, 77.4f, 77.9f, 79.1f, 84.7f, 91.1f, 103.8f, 93.2f, 108.9f, 94.5f, 104.7f)),
    BodyPolygon("FOREARM", floatArrayOf(18.7f, 79.6f, 22.1f, 77.9f, 20.9f, 84.3f, 9.4f, 103.0f, 6.8f, 108.5f, 5.1f, 104.7f)),
    BodyPolygon("GLUTEAL", floatArrayOf(44.7f, 99.6f, 30.2f, 108.5f, 29.8f, 118.7f, 31.5f, 126.0f, 47.2f, 121.3f, 49.4f, 114.9f)),
    BodyPolygon("GLUTEAL", floatArrayOf(55.3f, 99.1f, 51.1f, 114.5f, 52.3f, 120.9f, 68.1f, 126.0f, 69.8f, 119.1f, 69.4f, 108.5f)),
    BodyPolygon("ABDUCTOR", floatArrayOf(48.1f, 123.0f, 44.7f, 123.0f, 41.3f, 125.5f, 45.1f, 144.3f, 48.5f, 135.7f, 48.9f, 129.4f)),
    BodyPolygon("ABDUCTOR", floatArrayOf(51.9f, 122.6f, 55.7f, 123.4f, 59.1f, 126.0f, 54.9f, 144.3f, 51.9f, 136.2f, 51.1f, 129.4f)),
    BodyPolygon("HAMSTRING", floatArrayOf(28.9f, 122.1f, 31.1f, 129.4f, 36.6f, 126.0f, 35.3f, 135.3f, 34.5f, 150.2f, 29.4f, 158.3f, 28.9f, 146.8f, 27.7f, 141.3f, 27.2f, 131.5f)),
    BodyPolygon("HAMSTRING", floatArrayOf(71.5f, 121.7f, 69.4f, 128.9f, 63.8f, 126.0f, 65.5f, 136.6f, 66.4f, 150.2f, 71.1f, 158.3f, 71.5f, 147.7f, 72.8f, 142.1f, 73.6f, 131.9f)),
    BodyPolygon("HAMSTRING", floatArrayOf(38.7f, 125.5f, 44.3f, 146.0f, 40.4f, 166.8f, 36.2f, 152.8f, 37.0f, 135.3f)),
    BodyPolygon("HAMSTRING", floatArrayOf(61.7f, 125.5f, 63.4f, 136.2f, 64.3f, 153.2f, 60.0f, 166.8f, 56.2f, 146.4f)),
    BodyPolygon("KNEES", floatArrayOf(34.5f, 153.2f, 31.1f, 159.1f, 33.6f, 166.4f, 37.4f, 162.6f)),
    BodyPolygon("KNEES", floatArrayOf(66.4f, 153.6f, 63.0f, 163.0f, 66.8f, 166.4f, 69.4f, 159.1f)),
    BodyPolygon("CALVES", floatArrayOf(29.4f, 160.4f, 28.5f, 167.2f, 24.7f, 179.6f, 23.8f, 192.8f, 25.5f, 197.0f, 28.5f, 193.2f, 29.8f, 180.0f, 31.9f, 171.1f, 31.9f, 166.8f)),
    BodyPolygon("CALVES", floatArrayOf(37.4f, 165.1f, 35.3f, 167.7f, 33.2f, 171.9f, 31.1f, 180.4f, 30.2f, 191.9f, 34.0f, 200.0f, 38.7f, 190.6f, 39.1f, 168.9f)),
    BodyPolygon("CALVES", floatArrayOf(63.0f, 165.1f, 61.3f, 168.5f, 61.7f, 190.6f, 66.4f, 199.6f, 70.6f, 191.9f, 68.9f, 179.6f, 66.8f, 170.2f)),
    BodyPolygon("CALVES", floatArrayOf(70.6f, 160.4f, 72.3f, 168.5f, 75.7f, 179.1f, 76.6f, 192.8f, 74.5f, 196.6f, 72.3f, 193.6f, 70.6f, 179.6f, 68.1f, 168.1f)),
    BodyPolygon("LEFT_SOLEUS", floatArrayOf(28.5f, 195.7f, 30.2f, 195.7f, 33.6f, 201.7f, 30.6f, 220.0f, 28.5f, 213.6f, 26.8f, 198.3f)),
    BodyPolygon("RIGHT_SOLEUS", floatArrayOf(69.8f, 195.7f, 71.9f, 195.7f, 73.6f, 198.3f, 71.9f, 213.2f, 70.2f, 219.6f, 67.2f, 202.1f)),
)
