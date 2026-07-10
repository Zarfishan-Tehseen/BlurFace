package com.example.blurface.data.ml

import com.example.blurface.domain.model.FaceInstance
import com.example.blurface.domain.model.Person
import kotlin.math.sqrt

/**
 * Clusters face instances into persons.
 *
 * Three rules that keep different people from collapsing into one cluster:
 *
 *  1. TRACKS FIRST — ML Kit tracking IDs are near-perfect within a shot, so all
 *     instances of one track are assigned as a single unit using a
 *     quality-weighted mean embedding (much more stable than per-frame vectors).
 *
 *  2. CO-OCCURRENCE CONSTRAINT — two faces visible in the same frame can never
 *     be the same person. A track is never assigned to (and clusters are never
 *     merged with) a person it overlaps with in time. This alone makes the
 *     "4 people become 3" failure impossible when the people share frames.
 *
 *  3. CENTROID LINKAGE — distance is measured against the L2-normalized mean
 *     embedding of the whole cluster, not the closest single face. Min-distance
 *     ("single linkage") lets one noisy face chain two people together.
 *
 * Thresholds are for cosine distance on L2-normalized FaceNet-512 embeddings:
 * same person in video is typically 0.10–0.30, different people 0.55+.
 */
class PeopleClusterer(
    private val threshold: Float = 0.35f,        // assign track -> existing person
    private val mergeThreshold: Float = 0.30f,   // merge person <-> person (stricter!)
    private val sharedFrameTolerance: Int = 1    // allow 1 shared frame (double-detection noise)
) {
    val people = mutableListOf<Person>()

    fun clusterAll(instances: List<FaceInstance>) {
        people.clear()
        if (instances.isEmpty()) return

        // Strongest tracks first so cluster seeds are high quality.
        val tracks = buildTracks(instances)
            .sortedByDescending { t -> t.sumOf { it.quality.toDouble() } }

        for (track in tracks) assignTrack(track)
        mergeSplits()
    }

    // ---------------------------------------------------------------- tracks

    /** Group instances by ML Kit trackingId; untracked faces become singleton tracks. */
    private fun buildTracks(instances: List<FaceInstance>): List<List<FaceInstance>> {
        val tracked = instances.filter { it.trackingId != null }
            .groupBy { it.trackingId }
            .values.toList()
        val untracked = instances.filter { it.trackingId == null }.map { listOf(it) }
        return tracked + untracked
    }

    /** Quality-weighted mean of the best faces in a track, L2-normalized. */
    private fun trackEmbedding(track: List<FaceInstance>): FloatArray {
        val top = track.sortedByDescending { it.quality }.take(5)
        val dim = top.first().embedding.size
        val mean = FloatArray(dim)
        var totalW = 0f
        for (f in top) {
            val w = f.quality.coerceAtLeast(1e-3f)
            totalW += w
            for (i in 0 until dim) mean[i] += f.embedding[i] * w
        }
        if (totalW > 0f) {
            for (i in 0 until dim) {
                mean[i] = mean[i] / totalW
            }
        }
        return l2(mean)
    }

    // ------------------------------------------------------------ assignment

    private fun assignTrack(track: List<FaceInstance>) {        val emb = trackEmbedding(track)
        val trackFrames = track.mapTo(HashSet()) { it.frameId }

        var bestPerson: Person? = null
        var bestDist = Float.MAX_VALUE

        for (person in people) {
            // HARD RULE: same person can't be in one frame twice.
            if (sharedFrames(framesOf(person), trackFrames) > sharedFrameTolerance) continue

            val dist = cosineDistance(centroidOf(person), emb)
            if (dist < bestDist) {
                bestDist = dist
                bestPerson = person
            }
        }

        android.util.Log.d(
            "FaceCluster",
            "Track ${track.first().trackingId} (${track.size} faces) -> " +
                    "best=${bestPerson?.id} dist=${"%.4f".format(bestDist)}"
        )

        if (bestPerson != null && bestDist < threshold) {
            track.forEach { bestPerson.add(it) }
        } else {
            val p = Person(track.first())
            track.drop(1).forEach { p.add(it) }
            people.add(p)
        }
    }

    // --------------------------------------------------------------- merging

    /**
     * Merge clusters that are actually the same person (e.g. the person left the
     * frame and came back with a new tracking ID). Uses a STRICTER threshold than
     * assignment and refuses to merge clusters that co-occur in time.
     */
    private fun mergeSplits() {
        var changed = true
        while (changed) {
            changed = false
            outer@ for (i in people.indices) {
                val framesI = framesOf(people[i])
                val centI = centroidOf(people[i])
                for (j in (i + 1) until people.size) {
                    if (sharedFrames(framesI, framesOf(people[j])) > sharedFrameTolerance) continue

                    val dist = cosineDistance(centI, centroidOf(people[j]))
                    if (dist < mergeThreshold) {
                        android.util.Log.d(
                            "FaceCluster",
                            "Merging Person${people[j].id} into Person${people[i].id} " +
                                    "(centroid dist=${"%.4f".format(dist)})"
                        )
                        val targetPerson = people[i]
                        people[j].instances.forEach { targetPerson.add(it) }
                        people.removeAt(j)
                        changed = true
                        break@outer
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------- helpers

    private fun framesOf(p: Person): Set<Int> =
        p.instances.mapTo(HashSet()) { it.frameId }

    private fun sharedFrames(a: Set<Int>, b: Set<Int>): Int {
        val (small, large) = if (a.size <= b.size) a to b else b to a
        var n = 0
        for (f in small) if (f in large) n++
        return n
    }

    /** L2-normalized mean of a cluster's best faces (recomputed, never stale). */
    private fun centroidOf(p: Person): FloatArray {
        val top = p.instances.sortedByDescending { it.quality }.take(10)
        val dim = top.first().embedding.size
        val mean = FloatArray(dim)
        for (f in top) for (i in 0 until dim) mean[i] += f.embedding[i]
        for (i in 0 until dim) {
            mean[i] = mean[i] / top.size.toFloat()
        }
        return l2(mean)
    }

    companion object {
        fun cosineDistance(a: FloatArray, b: FloatArray): Float {
            var dot = 0f; var normA = 0f; var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            if (normA == 0f || normB == 0f) return 1f
            return 1f - (dot / (sqrt(normA) * sqrt(normB)))
        }

        fun l2(v: FloatArray): FloatArray {
            var norm = 0f
            for (x in v) norm += x * x
            norm = sqrt(norm)
            if (norm == 0f) return v
            return FloatArray(v.size) { v[it] / norm }
        }
    }
}
