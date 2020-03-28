/*
 * Copyright (C) 2020 Hamidreza Etebarian & Ali Modares.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.ui.material.form

import android.app.Application
import androidx.lifecycle.MutableLiveData
import meow.core.arch.MeowViewModel
import sample.data.Content

/**
 * Material Alert View Model class.
 *
 * @author  Hamidreza Etebarian
 * @version 1.0.0
 * @since   2020-03-22
 */

class FormViewModel(app: Application) :
    MeowViewModel(app) { //todo @Ali find way for create this class automatically

    val listLiveData = MutableLiveData<List<Content>>() //todo @Hamid search single vs normal

}