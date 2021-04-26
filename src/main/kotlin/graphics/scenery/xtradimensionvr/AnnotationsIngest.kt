package graphics.scenery.xtradimensionvr

import ch.systemsx.cisd.base.mdarray.MDFloatArray
import ch.systemsx.cisd.base.mdarray.MDIntArray
import ch.systemsx.cisd.hdf5.HDF5Factory
import ch.systemsx.cisd.hdf5.IHDF5Reader
import graphics.scenery.Mesh
import graphics.scenery.numerics.Random
import hdf.hdf5lib.exceptions.HDF5SymbolTableException
import java.util.*
import kotlin.collections.ArrayList

class AnnotationsIngest(h5adPath: String) {
    val reader: IHDF5Reader = HDF5Factory.openForReading(h5adPath)
    private val geneNames = h5adAnnotationReader("/var/index")

    private val csrData: MDFloatArray = reader.float32().readMDArray("/X/data")
    private val csrIndices: MDIntArray = reader.int32().readMDArray("/X/indices")
    private val csrIndptr: MDIntArray = reader.int32().readMDArray("/X/indptr")

    private val cscData: MDFloatArray = reader.float32().readMDArray("/layers/X_csc/data")
    private val cscIndices: MDIntArray = reader.int32().readMDArray("/layers/X_csc/indices")
    private val cscIndptr: MDIntArray = reader.int32().readMDArray("/layers/X_csc/indptr")

    private val numGenes = reader.string().readArrayRaw("/var/index").size
    private val numCells = reader.string().readArrayRaw("/obs/index").size

    init{
        println(reader.getGroupMembers("/obs"))
    }

    fun fetchGeneExpression(): Pair<ArrayList<String>, ArrayList<FloatArray>> {
        val randGenesIndices = ArrayList<Int>()
        val randGenesNames = arrayListOf<String>()

        for (i in 0..12) {
            randGenesNames.add(geneNames[Random.randomFromRange(0f, numGenes.toFloat()).toInt()] as String)
        }
        for (i in randGenesNames)
            randGenesIndices.add(geneNames.indexOf(i))

        val geneExpression = ArrayList<FloatArray>()
        for (i in randGenesIndices) {
            geneExpression.add(cscReader(i))
        }
        return Pair(randGenesNames, geneExpression)
    }

    fun umapReader3D(): ArrayList<ArrayList<Float>> {
        val UMAP = arrayListOf<ArrayList<Float>>()

        var tripletCounter = 0
        val cellUMAP = ArrayList<Float>()

        for (coordinate in reader.float32().readArray("/obsm/X_umap")) {
            if (tripletCounter < 3) {
                cellUMAP.add(coordinate)
                tripletCounter += 1
            } else {
                UMAP.add(
                    arrayListOf(
                        cellUMAP[0],
                        cellUMAP[1],
                        cellUMAP[2]
                    )
                ) // actual values instead of pointer to object
                cellUMAP.clear()
                tripletCounter = 1 // zero for first loop, then 1, as first entry is added in else clause
                cellUMAP.add(coordinate)
            }
        }
        UMAP.add(arrayListOf(cellUMAP[0], cellUMAP[1], cellUMAP[2])) // add final sub-array

        return UMAP
    }

    fun h5adAnnotationReader(hdfPath: String, asString: Boolean = true): ArrayList<*> {
        /**
         * reads any 1 dimensional annotation (ie obs, var, uns from scanPy output), checking if a categorical map exists for them
         **/
        if (hdfPath[4].toString() != "/")
            throw InputMismatchException("this function is only for reading obs, var, and uns")

        val data = ArrayList<Any>()
        val categoryMap = hashMapOf<Int, String>()
        val annotation = hdfPath.substring(5) // returns just the annotation requested

        when {
            reader.getDataSetInformation(hdfPath).toString().contains("STRING") -> {// String
                for (i in reader.string().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(1)") && asString ->
                try {
                    for ((counter, category) in reader.string().readArray("/uns/" + annotation + "_categorical").withIndex())
                        categoryMap[counter] = category

                    for (i in reader.int8().readArray(hdfPath))
                        categoryMap[i.toInt()]?.let { data.add(it) }

                } catch (e: HDF5SymbolTableException) { // int8 but not mapped to categorical
                    for (i in reader.int8().readArray(hdfPath))
                        categoryMap[i.toInt()]?.let { data.add(it) }
                }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(1)") && !asString -> {// Byte
                for (i in reader.int8().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(2)") -> {// Short
                for (i in reader.int16().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(4)") -> {// Int
                for (i in reader.int32().readArray(hdfPath))
                    data.add(i)
            }
            reader.getDataSetInformation(hdfPath).toString().contains("INTEGER(8)") -> {// Long
                for (i in reader.int64().readArray(hdfPath))
                    data.add(i)
                }
            reader.getDataSetInformation(hdfPath).toString().contains("FLOAT(4)") -> {// Float
                for (i in reader.float32().readArray(hdfPath))
                    data.add(i)
                }
            reader.getDataSetInformation(hdfPath).toString().contains("FLOAT(8)") -> {// Double
                for (i in reader.float64().readArray(hdfPath))
                    data.add(i)
                }
            reader.getDataSetInformation(hdfPath).toString().contains("BOOLEAN") -> {// Boolean
                for (i in reader.int8().readArray(hdfPath))
                    data.add(i)
                }
        }
        return data
    }

    fun fetchMostExpressedGenes(cells: ArrayList<Mesh>, numGenes: Int) {
        val cellIndices = cells.map { it.name.toInt() }
        val geneExpressions: ArrayList<HashMap<String, Float>> = cellIndices.run {
            this.forEach {
                for (i in 0..numGenes) {

                }
            }


            ArrayList<HashMap<String, Float>>()
        }
    }

    /**
     * return dense row of gene expression values for a chosen row / cell
     */
    private fun csrReader(row: CellIndex = 0): FloatArray {
        // init float array of zeros the length of the number of genes
        val exprArray = FloatArray(numGenes)

        // slice pointer array to give the number of non-zeros in the row
        val start = csrIndptr[row]
        val end = csrIndptr[row+1]

        // from start index until (excluding) end index, substitute non-zero values into empty array
        for(i in start until end){
            exprArray[csrIndices[i]] = csrData[i]
        }
        return exprArray
    }

    /**
     * return dense column of gene expression values for a chosen column / gene
     */
    private fun cscReader(col: GeneIndex = 0): FloatArray {
        val exprArray = FloatArray(numCells)

        val start = cscIndptr[col]
        val end = cscIndptr[col+1]

        for(i in start until end){
            exprArray[cscIndices[i]] = cscData[i]
        }
        return exprArray
    }
}

fun main(){
    AnnotationsIngest("/home/luke/PycharmProjects/VRCaller/file_conversion/bbknn_processed.h5ad")
}