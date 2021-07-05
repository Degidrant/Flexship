package com.flexship.flexshipcookingass.ui.fragments

import android.app.Activity.RESULT_OK
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.flexship.flexshipcookingass.R
import com.flexship.flexshipcookingass.adapters.SpinnerAdapter
import com.flexship.flexshipcookingass.adapters.StageAdapter
import com.flexship.flexshipcookingass.databinding.FragmentDishBinding
import com.flexship.flexshipcookingass.models.Dish
import com.flexship.flexshipcookingass.models.Stages
import com.flexship.flexshipcookingass.other.Constans
import com.flexship.flexshipcookingass.other.zeroOrNotZero
import com.flexship.flexshipcookingass.ui.dialogs.MinutePickerDialog
import com.flexship.flexshipcookingass.ui.viewmodels.DishViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import pub.devrel.easypermissions.AppSettingsDialog

@AndroidEntryPoint
class DishFragment : Fragment(){

    private lateinit var stageAdapter: StageAdapter

    private lateinit var _binding: FragmentDishBinding
    private val binding get() = _binding

    private val args:DishFragmentArgs by navArgs()
    private val viewModel:DishViewModel by viewModels()

    private lateinit var dish: Dish
    private var stages = listOf<Stages>()

    private var bitmap: Bitmap ?= null
    private var imageUri: Uri ?= null

    private var timeSec: Int = 0
    private var dishId: Int = -1
    private val stageList: MutableList<Stages> = mutableListOf()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions->
        permissions.forEach { permission->
            if(!permission.value){
                if(ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),android.Manifest.permission.CAMERA)
                    || ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),
                        android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    || ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                    alertDialog("Handling permissions"
                        ,"Please accept all the permissions to be able to set an image of your dish...")
                }
                else{
                    AppSettingsDialog.Builder(this).build().show()
                }
                return@registerForActivityResult
            }
        }
        binding.bAddImage.isEnabled=true
    }
    private val getPhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result->
        if(result.resultCode==RESULT_OK){
            result.data?.let {
                binding.mainImage.setImageURI(it.data)
                bitmap=convertUriToBitmap(it.data!!)
            }
        }
    }
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result->
        if(result.resultCode==RESULT_OK){
            imageUri?.let {
                binding.mainImage.setImageURI(it)
                bitmap=convertUriToBitmap(it)
            }
        }
    }




    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDishBinding.inflate(inflater, container, false)


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

            timeSec = savedInstanceState?.getInt(Constans.KEY_MINUTE) ?: 0


        if(savedInstanceState!= null){
            setTimeToButton(timeSec)
            val numberPickerDialog= parentFragmentManager
                .findFragmentByTag(Constans.TAG_MINUTE_PICKER) as MinutePickerDialog?

            numberPickerDialog?.setAction { time->
                this.timeSec = time
                setTimeToButton(timeSec)
            }
        }

        checkPermissions()
        stageAdapter = StageAdapter(requireContext())
        updateListUI()


        if(args.dishId!=-1){
            dishId=args.dishId
            viewModel.getDishById(args.dishId).observe(viewLifecycleOwner){
                    dishWithStages->
                dish = dishWithStages.dish
                stages=dishWithStages.stages
                setValues()
            }
        }else{
            dish = Dish()
            viewModel.insertDish(dish)
            viewModel.getNewDish().observe(viewLifecycleOwner){
                dishId=it
            }
        }

        requireActivity().actionBar?.apply {
            setDisplayShowHomeEnabled(true)
            title=if(dish.id <= 0){
                "Новое блюдо"
            }
            else{
                "Блюдо: ${dish.name}"
            }
        }

        val textForSpinner= arrayOf("Супы","Закуски","Салаты","Пиццы","Горячее","Завтрак","Десерты")
        val imagesForSpinner= arrayOf(R.drawable.soup, R.drawable.snack, R.drawable.salad, R.drawable.pizza, R.drawable.thanksgiving, R.drawable.breakfast,
        R.drawable.vegan)

        binding.spinnerCategory.apply {
            adapter = SpinnerAdapter(requireContext(),textForSpinner,imagesForSpinner)
        }

    }

    private fun setTimeToButton(timeSec: Int) {
        binding.bTime.text = String.format(getString(R.string.timer), zeroOrNotZero(timeSec / 60), zeroOrNotZero(timeSec % 60))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId==android.R.id.home){
            findNavController().popBackStack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setValues() = with(binding){
            edName.setText(dish.name)
            edReceipt.setText(dish.recipe)
            if(dish.image!=null)
                mainImage.setImageBitmap(dish.image)
            else
                mainImage.setImageResource(R.drawable.empty)
            stageAdapter.differ.submitList(stages)
    }

    override fun onStart()= with(binding) {
        super.onStart()

        bAddImage.setOnClickListener {
            showPopupMenu()
        }

        bAddStage.setOnClickListener {
            checkFieldsForAddStage()
        }

        bInsertDish.setOnClickListener {

        }

        bTime.setOnClickListener {
            MinutePickerDialog().apply {
                setAction {
                    seconds ->
                    timeSec = seconds
                    setTimeToButton(timeSec)
                }
            }.show(parentFragmentManager,Constans.TAG_MINUTE_PICKER)
        }

    }

    private fun checkFieldsForAddStage() {
        binding.apply {
        val stageName = edStages.text.toString()
        if(stageName.isNotEmpty()){
            val stage = Stages(name = stageName, time = timeSec, dishId = dishId)
            stageList.add(stage)
            stageAdapter.differ.submitList(stageList)
            timeSec = 0
            edStages.setText("")
            bTime.setText(R.string.dish_choose_time)
            updateListUI()
        }
        else {
            Snackbar.make(requireView(),"Введите название этапа", Snackbar.LENGTH_SHORT).show()
        }
        }
    }

    private fun updateListUI() {
        binding.recViewStages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = stageAdapter
        }
    }


    private fun showPopupMenu(){
        PopupMenu(requireContext(),binding.bAddImage).apply {
            inflate(R.menu.photo_menu)
            setOnMenuItemClickListener {
                item->
                if(item.itemId==R.id.get_photo){
                    getPhotoFromGallery()
                    return@setOnMenuItemClickListener false
                }
                if(item.itemId==R.id.take_photo){
                    takePhoto()
                    return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setForceShowIcon(true)
            }
        }.also {
            it.show()
        }
    }

    private fun convertUriToBitmap(uri: Uri): Bitmap {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source= ImageDecoder.createSource(requireContext().contentResolver,
                uri)
            ImageDecoder.decodeBitmap(source)
        } else{
            MediaStore.Images.Media.getBitmap(requireContext().contentResolver,uri)
        }
    }
    private fun takePhoto(){
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "New Picture")
            put(MediaStore.Images.Media.DESCRIPTION, "From camera")
        }
        imageUri = requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        }.also {
            takePhotoLauncher.launch(it)
        }

    }
    private fun getPhotoFromGallery(){
        Intent().apply {
            action= Intent.ACTION_OPEN_DOCUMENT
            type="image/*"
        }.also {
            getPhotoLauncher.launch(it)
        }
    }

    //PERMISSIONS
    private fun checkPermissions(){
        when{
            ContextCompat.checkSelfPermission(requireContext(),android.Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(requireContext(),android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(requireContext(),android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED ->{
                binding.bAddImage.isEnabled=true
            }
            ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),android.Manifest.permission.CAMERA)
                    || ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),
                android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    || ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)->{
                alertDialog("Handling permissions"
                    ,"Please accept all the permissions to be able to set an image of your dish...")
            }
            else->{
                requestPermissions()
            }
        }
    }

    private fun requestPermissions(){
        permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    private fun alertDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ok"){_,_->
                requestPermissions()
            }
            .setNegativeButton("No",null)
            .create()
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(Constans.KEY_MINUTE, timeSec)
    }



}